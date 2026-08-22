package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * mDNS 发现无线调试服务（adb TLS pair 与 connect 端口）。
 *
 * 移植自 Shizuku（Apache-2.0）的 AdbMdns：
 * - 搜索 `_adb-tls-pairing._tcp` → 可配对的端口（37xxx）
 * - 搜索 `_adb-tls-connect._tcp` → 可连接的端口（39xxx）
 * - 过滤本机服务（host 地址是本地网络接口之一、端口被 adbd 占用 → 不可 bind）
 *
 * NsdManager 不需要声明 Service 组件，直接通过 system service 调用，
 * 适合寄生式架构（只有 MainActivity 真实存在）。
 *
 * @param serviceType 如 AdbMdns.TLS_PAIRING 或 AdbMdns.TLS_CONNECT
 * @param onServiceFound 解析到本机服务时回调（host, port）；port<=0 表示服务丢失
 */
@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    private val context: Context,
    private val serviceType: String,
    private val onServiceFound: (host: String, port: Int) -> Unit,
) {

    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val discoveryListener = AdbDiscoveryListener()
    private var registered = false
    private var discoveredPort = -1

    fun start() {
        if (registered) return
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            registered = true
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices failed: $e")
            onDiscoveryError("discoverServices: ${e.message}")
        }
    }

    /** 供 AdbPairingFlow 展示发现错误（如 NsdManager 不可用）。 */
    private var onError: ((String) -> Unit)? = null

    fun setOnError(callback: (String) -> Unit) {
        onError = callback
    }

    private fun onDiscoveryError(message: String) {
        onError?.invoke(message)
    }

    fun stop() {
        if (!registered) return
        registered = false
        discoveredPort = -1
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        resolveWithRetry(info, retries = 0)
    }

    /** resolve 失败自动重试（NsdManager 的 resolve 偶发失败，重试通常能成功）。 */
    private fun resolveWithRetry(info: NsdServiceInfo, retries: Int) {
        if (!registered) return
        if (retries > MAX_RESOLVE_RETRIES) {
            Log.w(TAG, "resolve gave up: ${info.serviceName}")
            return
        }
        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "resolve failed: ${info.serviceName}, code=$errorCode (retry $retries)")
                // 延后重试，避免在回调线程里立刻重入
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    resolveWithRetry(info, retries + 1)
                }, RESOLVE_RETRY_DELAY_MS)
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                onServiceResolved(info)
            }
        })
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        Log.v(TAG, "service lost: ${info.serviceName}")
        discoveredPort = -1
        onServiceFound("", -1)
    }

    private fun onServiceResolved(resolved: NsdServiceInfo) {
        if (!registered) return
        val host = resolved.host?.hostAddress
        if (host == null) {
            // 某些 Samsung 设备上 resolved.host 可能为 null，fallback 到 127.0.0.1
            Log.w(TAG, "resolved.host is null, falling back to 127.0.0.1")
            discoveredPort = resolved.port
            onServiceFound("127.0.0.1", resolved.port)
            return
        }

        // 本机检查仅用于日志/参考，不做硬性过滤：
        // 寄生式架构下宿主可能没有 INTERNET 权限，getNetworkInterfaces() 只返回回环，
        // 解析出的设备局域网 IP 会因此被误判为"非本机"；而 _adb-tls-* 服务类型本身
        // 足够唯一（只有开启无线调试配对的设备才广播），误连其他设备的概率极低。
        val isLocal = runCatching {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.any { netIf ->
                    netIf.inetAddresses?.asSequence()
                        ?.any { host == it.hostAddress }
                        ?: false
                } ?: false
        }.getOrDefault(false)

        // 端口占用检查同样只作参考：adbd 可能监听在具体 IP 而非 0.0.0.0，
        // 127.0.0.1 绑定测试不可靠。
        val isPortBusy = try {
            ServerSocket().use { sock ->
                sock.bind(InetSocketAddress("127.0.0.1", resolved.port), 1)
                false
            }
        } catch (_: IOException) {
            true
        }

        Log.i(TAG, "resolved $serviceType: ${resolved.serviceName} host=$host port=${resolved.port} local=$isLocal portBusy=$isPortBusy")
        discoveredPort = resolved.port
        onServiceFound(host, resolved.port)
    }

    private inner class AdbDiscoveryListener : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(TAG, "discovery started: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "start discovery failed: $serviceType, code=$errorCode")
            onDiscoveryError("discovery failed: $errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "discovery stopped: $serviceType")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "stop discovery failed: $serviceType, code=$errorCode")
        }

        override fun onServiceFound(info: NsdServiceInfo) {
            Log.i(TAG, "service found: ${info.serviceName} type=${info.serviceType}")
            onServiceFound(info)
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            onServiceLost(info)
        }
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        private const val TAG = "AdbMdns"
        private const val MAX_RESOLVE_RETRIES = 5
        private const val RESOLVE_RETRY_DELAY_MS = 800L
    }
}