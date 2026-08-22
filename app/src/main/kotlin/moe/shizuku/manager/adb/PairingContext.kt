package moe.shizuku.manager.adb

import android.util.Log

/**
 * SPAKE2 配对上下文的 JNI 封装。
 *
 * 注意：包名与类名必须保持 `moe.shizuku.manager.adb.PairingContext` ——
 * libadb.so 的 JNI_OnLoad 用 RegisterNatives 绑定到这个类，
 * 改包名会导致 System.loadLibrary("adb") 时 FindClass 失败。
 *
 * 协议实现来自 Shizuku（Apache-2.0），本文件为兼容移植。
 */
internal class PairingContext private constructor(private val nativePtr: Long) {

    val msg: ByteArray

    init {
        msg = nativeMsg(nativePtr)
    }

    fun initCipher(theirMsg: ByteArray) = nativeInitCipher(nativePtr, theirMsg)

    fun encrypt(`in`: ByteArray) = nativeEncrypt(nativePtr, `in`)

    fun decrypt(`in`: ByteArray) = nativeDecrypt(nativePtr, `in`)

    fun destroy() = nativeDestroy(nativePtr)

    private external fun nativeMsg(nativePtr: Long): ByteArray

    private external fun nativeInitCipher(nativePtr: Long, theirMsg: ByteArray): Boolean

    private external fun nativeEncrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?

    private external fun nativeDecrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?

    private external fun nativeDestroy(nativePtr: Long)

    companion object {

        fun create(password: ByteArray): PairingContext? {
            val nativePtr = nativeConstructor(true, password)
            return if (nativePtr != 0L) PairingContext(nativePtr) else null
        }

        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long
    }
}

/** 加载 libadb.so（含 SPAKE2 配对实现）。仅 arm64-v8a。 */
fun loadAdbLibrary(): Boolean = try {
    System.loadLibrary("adb")
    Log.d("AdbPairClient", "libadb.so loaded")
    true
} catch (t: Throwable) {
    Log.e("AdbPairClient", "Failed to load libadb.so", t)
    false
}
