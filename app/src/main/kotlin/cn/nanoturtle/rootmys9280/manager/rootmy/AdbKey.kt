package cn.nanoturtle.rootmys9280.manager.rootmy

import android.annotation.SuppressLint
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.Principal
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream

/**
 * 无线调试 ADB 认证密钥：RSA-2048 密钥对 + Android 二进制公钥格式 + 自签名 X509 证书。
 *
 * 参考 Shizuku（Apache-2.0）的 AdbKey：
 * - [adbPublicKey]：Android 二进制 RSAPublicKey 结构体（524B）base64 + " <name>\0"，
 *   与 adb 的 authorized_keys 格式一致，配对 PeerInfo 与 AUTH_RSAPUBLICKEY 都发它
 * - [sslContext]：TLSv1.3 客户端认证上下文（自签名证书，CN=00），
 *   用于 adb pair（TLS + SPAKE2）与 connect（STLS 升级）两个阶段
 */
class AdbKey(private val keyPair: KeyPair, private val name: String = "adbkey") {

    private val privateKey: RSAPrivateKey = keyPair.private as RSAPrivateKey
    private val publicKey: RSAPublicKey = keyPair.public as RSAPublicKey

    // ---- Android 二进制公钥（与 Shizuku 相同实现）----

    val adbPublicKey: ByteArray by lazy { publicKey.adbEncoded(name) }

    /** 对 AUTH token 签名（adb 认证算法是 RSA-SHA1）。 */
    fun sign(data: ByteArray?): ByteArray {
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initSign(privateKey)
        sig.update(data ?: ByteArray(0))
        return sig.sign()
    }

    // ---- TLS 客户端认证（配对 & STLS 连接共用）----

    private val certificate: X509Certificate by lazy {
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val holder = X509v3CertificateBuilder(
            X500Name("CN=00"),
            BigInteger.ONE,
            Date(0),
            Date(2461449600 * 1000L),
            Locale.ROOT,
            X500Name("CN=00"),
            SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        ).build(signer)
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(holder.encoded)) as X509Certificate
    }

    private val keyManager = object : X509ExtendedKeyManager() {
        private val alias = "key"

        override fun chooseClientAlias(keyTypes: Array<out String>, issuers: Array<out Principal>?, socket: Socket?): String? =
            if (keyTypes.contains("RSA")) alias else null

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            if (alias == this.alias) arrayOf(certificate) else null

        override fun getPrivateKey(alias: String?): java.security.PrivateKey? =
            if (alias == this.alias) privateKey else null

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun getServerAliases(keyType: String, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String, issuers: Array<out Principal>?, socket: Socket?): String? = null
    }

    // ADB 无线配对使用自签名证书，认证靠配对码（TLS-PAIRING 协议），
    // 信任任意证书是协议要求，非安全缺陷。
    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private val trustManager = object : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val sslContext: SSLContext by lazy {
        val ctx = SSLContext.getInstance("TLSv1.3")
        ctx.init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
        ctx
    }
}

// ---- Android 二进制公钥编码（https://cs.android.com/.../libcrypto_utils/android_pubkey.c）----

private const val ANDROID_PUBKEY_MODULUS_SIZE = 2048 / 8
private const val ANDROID_PUBKEY_MODULUS_SIZE_WORDS = ANDROID_PUBKEY_MODULUS_SIZE / 4
private const val RSAPublicKey_Size = 524

private fun BigInteger.toAdbEncoded(): IntArray {
    val encoded = IntArray(ANDROID_PUBKEY_MODULUS_SIZE_WORDS)
    val r32 = BigInteger.ZERO.setBit(32)
    var tmp = this
    for (i in 0 until ANDROID_PUBKEY_MODULUS_SIZE_WORDS) {
        val out = tmp.divideAndRemainder(r32)
        tmp = out[0]
        encoded[i] = out[1].toInt()
    }
    return encoded
}

private fun RSAPublicKey.adbEncoded(name: String): ByteArray {
    /*
     * typedef struct RSAPublicKey {
     *     uint32_t modulus_size_words;
     *     uint32_t n0inv;      // -1 / N[0] mod 2^32
     *     uint8_t modulus[ANDROID_PUBKEY_MODULUS_SIZE];
     *     uint8_t rr[...];     // (2^rsa_size)^2 mod N
     *     uint32_t exponent;
     * } RSAPublicKey;
     */
    val r32 = BigInteger.ZERO.setBit(32)
    val n0inv = modulus.remainder(r32).modInverse(r32).negate()
    val r = BigInteger.ZERO.setBit(ANDROID_PUBKEY_MODULUS_SIZE * 8)
    val rr = r.modPow(BigInteger.valueOf(2), modulus)

    val buffer = ByteBuffer.allocate(RSAPublicKey_Size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(ANDROID_PUBKEY_MODULUS_SIZE_WORDS)
    buffer.putInt(n0inv.toInt())
    modulus.toAdbEncoded().forEach { buffer.putInt(it) }
    rr.toAdbEncoded().forEach { buffer.putInt(it) }
    buffer.putInt(publicExponent.toInt())

    val base64Bytes = Base64.getEncoder().encode(buffer.array())
    val nameBytes = " $name\u0000".toByteArray(Charsets.UTF_8)
    val bytes = ByteArray(base64Bytes.size + nameBytes.size)
    base64Bytes.copyInto(bytes)
    nameBytes.copyInto(bytes, base64Bytes.size)
    return bytes
}
