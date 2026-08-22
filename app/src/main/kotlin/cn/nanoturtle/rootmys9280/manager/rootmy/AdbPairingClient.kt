package cn.nanoturtle.rootmys9280.manager.rootmy

import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket
import moe.shizuku.manager.adb.PairingContext
import moe.shizuku.manager.adb.loadAdbLibrary

/**
 * adb pair（配对）客户端 —— 移植自 Shizuku（Apache-2.0）。
 *
 * 流程（与 `adb pair IP:PORT 配对码` 完全一致）：
 * 1. TLS 连接配对端口（37xxx，无线调试设置里「使用配对码配对设备」的端口）
 * 2. 从 TLS 会话导出 keying material，与配对码拼接成 SPAKE2 密码
 * 3. SPAKE2 交换（native libadb.so）→ 派生 AES-128-GCM 会话密钥
 * 4. 加密交换 PeerInfo（我们的 RSA 公钥）→ 设备把公钥加入 authorized_keys
 *
 * 配对成功后，设备记住公钥：之后 connect（39xxx）无需再点 RSA 指纹框。
 *
 * @param pairCode 无线调试设置里显示的 6 位配对码
 */
class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairCode: String,
    private val key: AdbKey,
) : Closeable {

    private enum class State {
        Ready, ExchangingMsgs, ExchangingPeerInfo, Stopped
    }

    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null
    private var pairingContext: PairingContext? = null
    private var state: State = State.Ready

    private val peerInfo: PeerInfo = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.value, key.adbPublicKey)

    /** 配对是否成功（成功后设备已记住我们的公钥）。 */
    @Volatile
    var paired = false
        private set

    fun start(): Boolean {
        setupTlsConnection()

        state = State.ExchangingMsgs
        if (!doExchangeMsgs()) {
            state = State.Stopped
            return false
        }

        state = State.ExchangingPeerInfo
        if (!doExchangePeerInfo()) {
            state = State.Stopped
            return false
        }

        state = State.Stopped
        paired = true
        return true
    }

    private fun setupTlsConnection() {
        if (!loadAdbLibrary()) throw IllegalStateException("libadb.so unavailable")
        // Samsung 的 adbd 配对服务可能只监听 IPv6 通配 [::]（netstat 显示 [::]:port），
        // 连接 127.0.0.1（IPv4 回环）会被拒。依次尝试多个回环地址。
        val raw = openPairingSocket()
        raw.tcpNoDelay = true
        socket = raw

        val sslContext = key.sslContext
        val sslSocket = sslContext.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        sslSocket.startHandshake()

        inputStream = DataInputStream(sslSocket.inputStream)
        outputStream = DataOutputStream(sslSocket.outputStream)

        val pairCodeBytes = pairCode.toByteArray()
        val keyMaterial = exportKeyingMaterial(sslSocket)
        val passwordBytes = ByteArray(pairCodeBytes.size + keyMaterial.size)
        pairCodeBytes.copyInto(passwordBytes)
        keyMaterial.copyInto(passwordBytes, pairCodeBytes.size)

        val ctx = PairingContext.create(passwordBytes)
        checkNotNull(ctx) { "Unable to create PairingContext." }
        pairingContext = ctx
    }

    /** 依次尝试 127.0.0.1 / ::1 / mDNS host，第一个能连上的回环地址。 */
    private fun openPairingSocket(): Socket {
        val candidates = linkedSetOf<String>()
        candidates += "127.0.0.1"
        candidates += "::1"
        if (host.isNotBlank() && host != "127.0.0.1" && host != "::1") candidates += host
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                return Socket(candidate, port)
            } catch (t: Throwable) {
                lastError = t
                Log.w(TAG, "connect $candidate:$port failed: $t")
            }
        }
        throw lastError ?: java.net.ConnectException("no address candidates")
    }

    /**
     * 从 TLS 会话导出 RFC 5705 keying material。
     * Conscrypt 是 Android 系统内置库，但 exportKeyingMaterial 属于隐藏 API，
     * 用反射调用避免编译期依赖 hidden-api-stub。
     */
    private fun exportKeyingMaterial(sslSocket: SSLSocket): ByteArray {
        val cls = Class.forName("com.android.org.conscrypt.Conscrypt")
        val method = cls.getMethod(
            "exportKeyingMaterial",
            SSLSocket::class.java,
            String::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
        )
        return method.invoke(null, sslSocket, kExportedKeyLabel, null, kExportedKeySize) as ByteArray
    }

    private fun createHeader(type: PairingPacketHeader.Type, payloadSize: Int): PairingPacketHeader =
        PairingPacketHeader(kCurrentKeyHeaderVersion, type.value, payloadSize)

    private fun readHeader(): PairingPacketHeader? {
        val bytes = ByteArray(kPairingPacketHeaderSize)
        inputStream!!.readFully(bytes)
        return PairingPacketHeader.readFrom(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    }

    private fun writeHeader(header: PairingPacketHeader, payload: ByteArray) {
        val buffer = ByteBuffer.allocate(kPairingPacketHeaderSize).order(ByteOrder.BIG_ENDIAN)
        header.writeTo(buffer)
        outputStream!!.write(buffer.array())
        outputStream!!.write(payload)
    }

    private fun doExchangeMsgs(): Boolean {
        val msg = pairingContext!!.msg
        val ourHeader = createHeader(PairingPacketHeader.Type.SPAKE2_MSG, msg.size)
        writeHeader(ourHeader, msg)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.SPAKE2_MSG.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream!!.readFully(theirMessage)

        return pairingContext!!.initCipher(theirMessage)
    }

    private fun doExchangePeerInfo(): Boolean {
        val buf = ByteBuffer.allocate(kMaxPeerInfoSize).order(ByteOrder.BIG_ENDIAN)
        peerInfo.writeTo(buf)

        val outbuf = pairingContext!!.encrypt(buf.array()) ?: return false

        val ourHeader = createHeader(PairingPacketHeader.Type.PEER_INFO, outbuf.size)
        writeHeader(ourHeader, outbuf)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.PEER_INFO.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream!!.readFully(theirMessage)

        val decrypted = pairingContext!!.decrypt(theirMessage) ?: throw AdbInvalidPairingCodeException()
        return decrypted.size == kMaxPeerInfoSize
    }

    override fun close() {
        try {
            inputStream?.close()
        } catch (_: Throwable) {
        }
        try {
            outputStream?.close()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        if (state != State.Ready) {
            pairingContext?.destroy()
        }
    }

    companion object {
        private const val TAG = "AdbPairingClient"
        private const val kCurrentKeyHeaderVersion = 1.toByte()
        private const val kMinSupportedKeyHeaderVersion = 1.toByte()
        private const val kMaxSupportedKeyHeaderVersion = 1.toByte()
        private const val kMaxPeerInfoSize = 8192
        private const val kMaxPayloadSize = kMaxPeerInfoSize * 2

        private const val kExportedKeyLabel = "adb-label\u0000"
        private const val kExportedKeySize = 64

        private const val kPairingPacketHeaderSize = 6
    }
}

class AdbInvalidPairingCodeException : Exception("pairing code is wrong")

private class PeerInfo(val type: Byte, data: ByteArray) {
    val data = ByteArray(kMaxPeerInfoSize - 1)

    init {
        data.copyInto(this.data, 0, 0, data.size.coerceAtMost(kMaxPeerInfoSize - 1))
    }

    enum class Type(val value: Byte) {
        ADB_RSA_PUB_KEY(0.toByte()),
        ADB_DEVICE_GUID(0.toByte()),
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.put(type)
        buffer.put(data)
    }

    companion object {
        private const val kMaxPeerInfoSize = 8192

        fun readFrom(buffer: ByteBuffer): PeerInfo {
            val type = buffer.get()
            val data = ByteArray(kMaxPeerInfoSize - 1)
            buffer.get(data)
            return PeerInfo(type, data)
        }
    }
}

private class PairingPacketHeader(val version: Byte, val type: Byte, val payload: Int) {
    enum class Type(val value: Byte) {
        SPAKE2_MSG(0.toByte()),
        PEER_INFO(1.toByte())
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.put(version)
        buffer.put(type)
        buffer.putInt(payload)
    }

    companion object {
        private const val kMinSupportedKeyHeaderVersion = 1.toByte()
        private const val kMaxSupportedKeyHeaderVersion = 1.toByte()
        private const val kMaxPayloadSize = 8192 * 2

        fun readFrom(buffer: ByteBuffer): PairingPacketHeader? {
            val version = buffer.get()
            val type = buffer.get()
            val payload = buffer.int

            if (version < kMinSupportedKeyHeaderVersion || version > kMaxSupportedKeyHeaderVersion) return null
            if (type != Type.SPAKE2_MSG.value && type != Type.PEER_INFO.value) return null
            if (payload <= 0 || payload > kMaxPayloadSize) return null

            return PairingPacketHeader(version, type, payload)
        }
    }
}
