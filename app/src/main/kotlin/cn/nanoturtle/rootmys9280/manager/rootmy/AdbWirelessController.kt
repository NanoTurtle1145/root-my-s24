package cn.nanoturtle.rootmys9280.manager.rootmy

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.concurrent.thread

/**
 * 无线调试授权执行器：纯 Kotlin 实现的 ADB 协议客户端。
 *
 * 使用 Android 11+ 的「开发者选项 → 无线调试」直连授权：
 * 1. 用户开启无线调试，App 内输入「IP:端口」（系统设置里显示的连接端口 39xxx）
 * 2. 本类用 RSA 密钥对走 ADB 协议认证（首次设备会弹 RSA 指纹确认，点允许）
 * 3. 认证通过后以 shell (uid 2000) 权限执行命令 —— 与 Shizuku 同权限等级
 *
 * 协议参考：Android 平台 tools adb 的 host 协议（CNXN/AUTH/OPEN/WRTE/CLSE）。
 * 不做 mDNS 自动发现（保持轻量），端口由用户从系统设置读取输入。
 */
object AdbWirelessController : ShellExecutor {

    // ---- ADB 协议常量 ----
    private const val CMD_CNXN = 0x4e584e43 // "CNXN"
    private const val CMD_AUTH = 0x48545541 // "AUTH"
    private const val CMD_OPEN = 0x4e45504f // "OPEN"
    private const val CMD_OKAY = 0x59414b4f // "OKAY"
    private const val CMD_CLSE = 0x45534c43 // "CLSE"
    private const val CMD_WRTE = 0x45545257 // "WRTE"
    private const val CMD_READ = 0x44414552 // "READ"

    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3

    private const val VERSION = 0x01000000
    private const val MAX_PAYLOAD = 4096

    /** 与 Shizuku shell 相同的最大等待时长。 */
    private const val CONNECT_TIMEOUT_MS = 8_000
    /** 认证等待：首次连接需用户点击设备上的 RSA 指纹允许框，留足时间 */
    private const val AUTH_TIMEOUT_MS = 30_000

    // ---- 状态 ----
    @Volatile private var socket: Socket? = null
    @Volatile private var input: DataInputStream? = null
    @Volatile private var output: OutputStream? = null
    private var keyPair: KeyPair? = null
    private var nextLocalId = 1

    /** 全局 reader 线程：连接建立后启动，统一读取所有消息并按 remoteId 分发。 */
    @Volatile private var readerThread: Thread? = null

    /** 等待 OPEN OKAY 的 future（localId → remoteId 完成值） */
    private val pendingOpens = HashMap<Int, java.util.concurrent.CompletableFuture<Int>>()

    /** 活跃通道（remoteId → AdbProcess），reader 据此分发 WRTE/CLSE */
    private val activeProcesses = HashMap<Int, AdbProcess>()

    @Volatile private var keyFileDir: File? = null

    /** 由 RootViewModel 在启动时注入密钥存储目录（App 私有目录）。 */
    fun init(keyDir: File) {
        keyFileDir = keyDir
        keyPair = loadOrCreateKeyPair(keyDir)
    }

    fun isConnected(): Boolean {
        val s = socket ?: return false
        return !s.isClosed && s.isConnected
    }

    override fun isReady(): Boolean = isConnected()

    /** 关闭连接。 */
    fun disconnect() {
        readerThread?.interrupt()
        readerThread = null
        synchronized(pendingOpens) {
            pendingOpens.values.forEach { it.completeExceptionally(IllegalStateException("disconnected")) }
            pendingOpens.clear()
        }
        synchronized(activeProcesses) {
            activeProcesses.values.forEach { it.markClosed() }
            activeProcesses.clear()
        }
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    // ================= ADB 传输层 =================

    private class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray) {
        val payloadString: String get() = String(payload, Charsets.UTF_8)
    }

    private fun readMessage(): AdbMessage {
        val din = input ?: throw IllegalStateException("not connected")
        val buf = ByteArray(24)
        var off = 0
        while (off < 24) {
            val n = din.read(buf, off, 24 - off)
            if (n < 0) throw java.io.EOFException("adb connection closed")
            off += n
        }
        // ADB 消息头全部为 little-endian
        val command = leInt(buf, 0)
        val arg0 = leInt(buf, 4)
        val arg1 = leInt(buf, 8)
        val length = leInt(buf, 12)
        if (length < 0 || length > 64 * 1024 * 1024) throw IllegalStateException("bad adb length $length")
        val payload = ByteArray(length)
        var po = 0
        while (po < length) {
            val n = din.read(payload, po, length - po)
            if (n < 0) throw java.io.EOFException("adb payload truncated")
            po += n
        }
        return AdbMessage(command, arg0, arg1, payload)
    }

    private fun sendMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)) {
        val out = output ?: throw IllegalStateException("not connected")
        val data = ByteArray(24 + payload.size)
        putLeInt(data, 0, command)
        putLeInt(data, 4, arg0)
        putLeInt(data, 8, arg1)
        putLeInt(data, 12, payload.size)
        putLeInt(data, 16, checksum(payload))
        putLeInt(data, 20, command xor -1)
        payload.copyInto(data, 24)
        synchronized(this) { out.write(data); out.flush() }
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum = (sum + (b.toInt() and 0xff)) and 0xffffffff.toInt()
        return sum
    }

    private fun leInt(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xff) or
            ((buf[off + 1].toInt() and 0xff) shl 8) or
            ((buf[off + 2].toInt() and 0xff) shl 16) or
            ((buf[off + 3].toInt() and 0xff) shl 24)

    private fun putLeInt(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v and 0xff).toByte()
        buf[off + 1] = ((v ushr 8) and 0xff).toByte()
        buf[off + 2] = ((v ushr 16) and 0xff).toByte()
        buf[off + 3] = ((v ushr 24) and 0xff).toByte()
    }

    // ================= 认证 =================

    /**
     * 连接并认证。首次连接时设备端会弹出 RSA 指纹确认框，
     * 用户点「允许」后完成认证（与电脑 adb connect 首次弹窗相同）。
     * @return 成功/失败；失败时 message 说明原因（如需要用户点允许）。
     */
    fun connect(host: String, port: Int): Pair<Boolean, String> {
        disconnect()
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.tcpNoDelay = true
            socket = s
            input = DataInputStream(s.getInputStream())
            output = s.getOutputStream()
            val kp = keyPair ?: throw IllegalStateException("key not initialized")
            authenticate(kp)
            // 认证完成，启动全局 reader 接收后续消息（OKAY/WRTE/CLSE）
            startGlobalReader()
            Pair(true, "connected")
        } catch (t: Throwable) {
            disconnect()
            Pair(false, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun authenticate(kp: KeyPair) {
        // 1. 发送 CNXN 握手
        sendMessage(CMD_CNXN, VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray(Charsets.UTF_8))

        // 2. 处理 AUTH 挑战循环（设备可能多次要求签名/公钥）
        val deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val msg = readMessage()
            when (msg.command) {
                CMD_AUTH -> when (msg.arg0) {
                    AUTH_TOKEN -> {
                        // 用私钥签名 token
                        val signed = signToken(kp.private, msg.payload)
                        sendMessage(CMD_AUTH, AUTH_SIGNATURE, 0, signed)
                    }
                    AUTH_RSAPUBLICKEY -> {
                        // 设备请求公钥（首次连接，弹 RSA 指纹确认框）
                        sendMessage(CMD_AUTH, AUTH_RSAPUBLICKEY, 0, sshRsaPublicKey(kp.public))
                    }
                    else -> throw IllegalStateException("unknown auth type ${msg.arg0}")
                }
                CMD_CNXN -> return // 认证完成
                else -> throw IllegalStateException("unexpected auth reply 0x${msg.command.toString(16)}")
            }
        }
        throw IllegalStateException("auth timeout")
    }

    private fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        // adb 认证签名算法是 RSA-SHA1（system/core/adb/auth.cpp: EVP_sha1）
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initSign(privateKey)
        sig.update(token)
        return sig.sign()
    }

    /** 生成 OpenSSH 格式公钥（adb 使用 ssh-rsa 格式 + comment）。 */
    private fun sshRsaPublicKey(public: PublicKey): ByteArray {
        val pub = (public as java.security.interfaces.RSAPublicKey)
        // OpenSSH mpint 编码：大端序、无前导零（toByteArray 可能带 0x00 前缀）
        val n = stripLeadingZero(pub.modulus.toByteArray())
        val e = stripLeadingZero(pub.publicExponent.toByteArray())
        val blob = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(blob)
        val sshRsa = "ssh-rsa".toByteArray(Charsets.UTF_8)
        dos.writeInt(sshRsa.size); dos.write(sshRsa)
        dos.writeInt(e.size); dos.write(e)
        dos.writeInt(n.size); dos.write(n)
        dos.flush()
        val b64 = Base64.getEncoder().encodeToString(blob.toByteArray())
        return ("ssh-rsa $b64 rootmys24@device\n\u0000").toByteArray(Charsets.UTF_8)
    }

    private fun stripLeadingZero(bytes: ByteArray): ByteArray {
        var i = 0
        while (i < bytes.size - 1 && bytes[i] == 0.toByte()) i++
        return if (i == 0) bytes else bytes.copyOfRange(i, bytes.size)
    }

    // ================= Shell 通道 =================

    override fun exec(cmd: Array<String>, env: Array<String>?, dir: String?): Process {
        // adb 的 shell/exec 服务本质上执行 `sh -c <整条命令>`，无法直接传 argv，
        // 所以把每个参数做单引号 shell 转义后拼接（与 adb 客户端行为一致）。
        val shellCmd = buildString {
            if (dir != null) append("cd ${escape(dir)} && ")
            if (env != null && env.isNotEmpty()) {
                append("env ")
                env.forEach { append(escape(it)).append(' ') }
            }
            append(cmd.joinToString(" ") { escape(it) })
        }
        // shell: 服务 —— adbd 用 `/system/bin/sh -c <整条命令>` 执行，
        // 支持 cd/env/&& 等 shell 语法（exec: 服务是直接 execve 分词，不支持）
        val remoteId = openShell("shell:$shellCmd")
        return AdbProcess(this, remoteId).also { registerProcess(remoteId, it) }
    }

    /** 单引号 shell 转义：'  ->  '\''  */
    private fun escape(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun openShell(service: String): Int {
        val localId = nextLocalId++
        val future = java.util.concurrent.CompletableFuture<Int>()
        synchronized(pendingOpens) { pendingOpens[localId] = future }
        sendMessage(CMD_OPEN, localId, 0, "$service\u0000".toByteArray(Charsets.UTF_8))
        // 全局 reader 收到 OKAY 后完成 future
        return try {
            future.get(15, java.util.concurrent.TimeUnit.SECONDS)
        } catch (t: Throwable) {
            synchronized(pendingOpens) { pendingOpens.remove(localId) }
            throw IllegalStateException("open shell timeout: $service", t)
        }
    }

    /**
     * 启动全局 reader：连接认证完成后调用一次。
     * 统一读取所有消息：OKAY 完成 pending future，WRTE 分发给对应通道，CLSE 标记通道结束。
     */
    private fun startGlobalReader() {
        if (readerThread != null) return
        val t = thread(name = "adb-global-reader", isDaemon = true) {
            try {
                while (true) {
                    val msg = readMessage()
                    when (msg.command) {
                        CMD_OKAY -> {
                            // arg0 = remote id, arg1 = local id
                            val remoteId = msg.arg0
                            val localId = msg.arg1
                            synchronized(pendingOpens) {
                                pendingOpens.remove(localId)?.complete(remoteId)
                            }
                        }
                        CMD_WRTE, CMD_READ -> {
                            // 服务端 WRTE: arg0 = remote id
                            val remoteId = msg.arg0
                            synchronized(activeProcesses) {
                                activeProcesses[remoteId]?.enqueueData(msg.payload)
                            }
                        }
                        CMD_CLSE -> {
                            val remoteId = msg.arg0
                            synchronized(activeProcesses) {
                                activeProcesses.remove(remoteId)?.markClosed()
                            }
                        }
                        CMD_CNXN -> { /* 忽略 */ }
                        else -> { /* 未知消息忽略 */ }
                    }
                }
            } catch (_: Throwable) {
                // 连接断开/异常：结束所有等待与活跃通道
                synchronized(pendingOpens) {
                    val it = pendingOpens.values.iterator()
                    while (it.hasNext()) it.next().completeExceptionally(IllegalStateException("adb disconnected"))
                    pendingOpens.clear()
                }
                synchronized(activeProcesses) {
                    val it = activeProcesses.values.iterator()
                    while (it.hasNext()) it.next().markClosed()
                    activeProcesses.clear()
                }
            }
        }
        readerThread = t
    }

    override fun capture(cmd: Array<String>): String {
        val process = exec(cmd)
        return try {
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) stdout + stderr else ""
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    override fun shell(cmd: String): Pair<Int, String> {
        val process = exec(arrayOf("/system/bin/sh", "-c", cmd))
        return try {
            val out = process.inputStream.bufferedReader().use { it.readText() } +
                process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor() to out
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    override fun writeFile(remotePath: String, mode: String, source: InputStream) {
        // 用 exec 启动 sh -c，把 source 写入其 stdin（rm 清旧文件防 root 属主覆盖失败）
        val process = exec(
            arrayOf(
                "sh", "-c",
                "rm -f '$remotePath' 2>/dev/null; cat > '$remotePath' && chmod $mode '$remotePath'"
            )
        )
        val err = StringBuilder()
        val exitCode = try {
            process.outputStream.use { output ->
                source.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            val errorReader = process.errorStream.bufferedReader()
            thread(name = "adb-writefile-err", isDaemon = true) {
                errorReader.forEachLine { err.append(it).append('\n') }
            }
            process.waitFor()
        } finally {
            if (process.isAlive) process.destroy()
        }
        check(exitCode == 0) {
            "Failed to stage $remotePath (exit $exitCode)${if (err.isNotBlank()) ": $err" else ""}"
        }
    }

    // ================= 密钥管理 =================

    private fun loadOrCreateKeyPair(dir: File): KeyPair {
        val privFile = File(dir, "adb_key")
        val pubFile = File(dir, "adb_key.pub")
        if (privFile.exists()) {
            try {
                val privBytes = privFile.readBytes()
                val pubBytes = pubFile.readBytes()
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(pub, priv)
            } catch (_: Throwable) {
                // 密钥损坏则重建
            }
        }
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val kp = gen.generateKeyPair()
        dir.mkdirs()
        privFile.writeBytes(kp.private.encoded)
        pubFile.writeBytes(kp.public.encoded)
        return kp
    }

    // ================= Process 包装 =================

    /**
     * 把 ADB shell 通道包装成 [Process]。
     *
     * 数据由全局 reader 线程按 remoteId 分发到本通道的队列：
     * - inputStream 读服务端输出，errorStream 复用同通道（adb shell 合并了 stderr）
     * - outputStream 写服务端 stdin
     * - [available] 反映队列中未读字节数（RootViewModel 的 drainProcessOutput 依赖它轮询）
     */
    private class AdbProcess(
        private val controller: AdbWirelessController,
        private val remoteId: Int,
    ) : Process() {
        private val readBuffer = java.util.concurrent.LinkedBlockingDeque<Byte?>()
        private val writeLock = Any()
        private val sharedStream by lazy { AdbInputStream() }
        private val inputStreamImpl: InputStream by lazy { sharedStream }
        private val outputStreamImpl: OutputStream by lazy { AdbOutputStream() }
        // adb shell 服务把 stderr 合并进同一通道，errorStream 与 inputStream 共享同一缓冲，
        // 避免两个独立流竞争消费数据（drainProcessOutput 先读 input 再读 error，不会丢）
        private val errorStreamImpl: InputStream by lazy { sharedStream }
        @Volatile private var exitCode: Int? = null
        private val exitLock = Object()

        /** 由全局 reader 调用：写入服务端发来的数据（过滤 pty 的 \r）。 */
        fun enqueueData(payload: ByteArray) {
            for (b in payload) {
                if (b.toInt() == '\r'.code) continue
                readBuffer.put(b)
            }
        }

        /** 由全局 reader 调用：服务端 CLSE 或连接断开。写入 EOF 哨兵唤醒阻塞的读。 */
        fun markClosed() {
            readBuffer.put(null)
            synchronized(exitLock) {
                exitCode = exitCode ?: 0
                exitLock.notifyAll()
            }
        }

        override fun getInputStream(): InputStream = inputStreamImpl
        override fun getOutputStream(): OutputStream = outputStreamImpl
        override fun getErrorStream(): InputStream = errorStreamImpl

        override fun waitFor(): Int {
            synchronized(exitLock) {
                while (exitCode == null) {
                    try {
                        exitLock.wait()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
            return exitCode ?: 0
        }

        override fun exitValue(): Int = exitCode ?: throw IllegalThreadStateException("process has not exited")

        override fun destroy() {
            runCatching { controller.sendMessage(CMD_CLSE, remoteId, 0) }
            controller.unregisterProcess(remoteId)
            synchronized(exitLock) {
                exitCode = exitCode ?: 0
                exitLock.notifyAll()
            }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = exitCode == null

        private inner class AdbInputStream : InputStream() {
            override fun available(): Int {
                var n = 0
                val it = readBuffer.iterator()
                while (it.hasNext()) {
                    if (it.next() != null) n++
                }
                return n
            }

            override fun read(): Int {
                // 阻塞等待：缓冲空且进程未结束时应挂起，直到有数据或 EOF 哨兵（null）
                while (true) {
                    val b = readBuffer.pollFirst()
                    if (b != null) return b.toInt() and 0xff
                    // 队列空：进程已结束（EOF 哨兵被消费）→ -1；否则短暂等待后重试
                    if (exitCode != null && readBuffer.isEmpty()) return -1
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return -1
                    }
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val first = read()
                if (first < 0) return -1
                b[off] = first.toByte()
                var count = 1
                while (count < len) {
                    val nxt = readBuffer.pollFirst() ?: break
                    if (nxt == null) break // EOF 哨兵：数据结束
                    b[off + count] = nxt
                    count++
                }
                return count
            }
        }

        private inner class AdbOutputStream : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                synchronized(writeLock) {
                    controller.sendMessage(CMD_WRTE, remoteId, 0, b.copyOfRange(off, off + len))
                }
            }
        }
    }

    /** 全局 reader 分发 WRTE 前调用：注册通道。 */
    private fun registerProcess(remoteId: Int, process: AdbProcess) {
        synchronized(activeProcesses) { activeProcesses[remoteId] = process }
    }

    /** destroy / CLSE 时调用：移除通道。 */
    private fun unregisterProcess(remoteId: Int) {
        synchronized(activeProcesses) { activeProcesses.remove(remoteId) }
    }
}
