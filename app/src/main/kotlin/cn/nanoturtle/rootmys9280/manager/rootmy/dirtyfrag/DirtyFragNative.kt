package cn.nanoturtle.rootmys9280.manager.rootmy.dirtyfrag

import android.util.Log

/**
 * DirtyFrag 引擎的 native 门面（netlink xfrm / splice / HMAC）。
 *
 * 为什么这一步必须是 native：见 `app/src/main/cpp/CMakeLists.txt` 的说明 ——
 * SA 的 SPI、`alg_trunc_len`（ICV 长度）、UDP 封装端口在 `IpSecManager` 里
 * 全由框架托管且不可回读，而 `splice(2)`/`vmsplice(2)` 根本没有公开 Java API。
 *
 * 库很小（几个文件、无第三方依赖）：AES-256 解密与 HMAC-SHA256 都是
 * 按 FIPS 197 / RFC 2104 自己实现的，已用标准测试向量验证。
 */
object DirtyFragNative {

    private const val TAG = "DirtyFragNative"

    init {
        System.loadLibrary("dfr")
    }

    /** 设置密钥与 ICV 截断位数（bit）。返回 0 成功，负值为 -errno。 */
    @JvmStatic
    external fun nativeInit(aesKey: ByteArray, authKey: ByteArray, truncBits: Int): Int

    /** 安装 xfrm SA。返回 0 成功，负值为 -errno。 */
    @JvmStatic
    external fun nativeSaAdd(spi: Int, encapPort: Int): Int

    /** 删除 xfrm SA。 */
    @JvmStatic
    external fun nativeSaDel(spi: Int): Int

    /** 开绑定在 127.0.0.1:port 且声明 UDP_ENCAP=ESPINUDP 的接收 socket。 */
    @JvmStatic
    external fun nativeOpenEncapSocket(port: Int): Int

    /** 开绑定在 srcPort、connect 到 dstPort 的普通 UDP 发送 socket。 */
    @JvmStatic
    external fun nativeOpenSenderSocket(srcPort: Int, dstPort: Int): Int

    /** 以 16 字节块改写文件页缓存（原语本体）。 */
    @JvmStatic
    external fun nativeWriteBlock16(
        udpFd: Int,
        path: String,
        offset: Long,
        spi: Int,
        seq: Int,
        desired: ByteArray,
        icvLen: Int,
    ): Int

    /** 关闭 fd。 */
    @JvmStatic
    external fun nativeClose(fd: Int): Int

    /**
     * 主动探测一次 native 调用是否真的可用，并记录失败原因。
     *
     * loadLibrary 成功 **不等于** 调用能成功：JNI 是按「类名+方法名+签名」
     * 解析实现的，若这些名字被 R8 改写，第一次调用会抛 UnsatisfiedLinkError。
     * 所以这里真的调一次，并把异常类型带出来，避免被"库没加载"误导。
     */
    fun probe(): String? =
        try {
            nativeSaDel(0)
            null
        } catch (t: Throwable) {
            Log.w(TAG, "native 探测失败", t)
            "${t.javaClass.simpleName}: ${t.message}"
        }

    fun isAvailable(): Boolean = probe() == null

    /** 把 -errno 翻译成可读文本。 */
    fun describe(code: Int): String =
        if (code >= 0) "ok" else "-${-code} ${errnoName(-code)}"

    private fun errnoName(e: Int): String =
        when (e) {
            1 -> "EPERM"
            13 -> "EACCES"
            17 -> "EEXIST"
            22 -> "EINVAL"
            2 -> "ENOENT"
            3 -> "ESRCH"
            5 -> "EIO"
            90 -> "EMSGSIZE"
            105 -> "ENOBUFS"
            95 -> "EOPNOTSUPP"
            else -> "errno"
        }
}
