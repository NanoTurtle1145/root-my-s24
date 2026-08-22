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
        }
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
        nsdManager.resolveService(info, AdbResolveListener())
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        Log.v(TAG, "service lost: ${info.serviceName}")
        discoveredPort = -1
        onServiceFound("", -1)
    }

    private fun onServiceResolved(resolved: NsdServiceInfo) {
        if (!registered) return
        // 过滤：必须是本机地址（回环或局域网 IP），且端口已被占用（adbd 已监听）
        val host = resolved.host?.hostAddress ?: return
        val isLocal = NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.any { netIf ->
                netIf.inetAddresses?.asSequence()
                    ?.any { host == it.hostAddress }
                    ?: false
            } ?: false
        if (!isLocal) {
            Log.v(TAG, "resolved service is not local: $host")
            return
        }
        val isPortBusy = try {
            ServerSocket().use { sock ->
                sock.bind(InetSocketAddress("127.0.0.1", resolved.port), 1)
                false
            }
        } catch (_: IOException) {
            true
        }
        if (!isPortBusy) return
        discoveredPort = resolved.port
        onServiceFound(host, resolved.port)
    }

    private inner class AdbDiscoveryListener : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "discovery started: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "start discovery failed: $serviceType, code=$errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "discovery stopped: $serviceType")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "stop discovery failed: $serviceType, code=$errorCode")
        }

        override fun onServiceFound(info: NsdServiceInfo) {
            Log.v(TAG, "service found: ${info.serviceName}")
            onServiceFound(info)
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            onServiceLost(info)
        }
    }

    private inner class AdbResolveListener : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
            Log.v(TAG, "resolve failed: ${info.serviceName}, code=$errorCode")
        }

        override fun onServiceResolved(info: NsdServiceInfo) {
            onServiceResolved(info)
        }
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        private const val TAG = "AdbMdns"
    }
}