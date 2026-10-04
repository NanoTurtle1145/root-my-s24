package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * 接收通知上 RemoteInput 配对码的 Service（与 Shizuku 的 AdbPairingService 等效）。
 *
 * 为什么必须用 Service 而不是 Activity：
 * 通知上的「输入配对码」确认后，若 PendingIntent 指向 Activity，系统会把该 Activity
 * 带到前台——用户被跳回 App，系统设置里的「使用配对码配对设备」页面随之失焦，
 * 三星上该页面失焦即关闭，配对服务随之停止，端口失效。
 *
 * Service 在后台处理，不打断配对码页面，用户全程停留在系统设置，配对码保持有效。
 *
 * 注意：本 Service 由 `PendingIntent.getForegroundService` 启动（见 AdbPairingFlow），
 * 因此**必须**在 onStartCommand 返回前调用 startForeground；否则系统在 Android 12+
 * 会直接判为「后台启动普通 Service」而拒绝启动，症状就是输完配对码毫无反应。
 */
class PairingReplyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        AdbPairingFlow.diag("收到配对码通知回调（Service 已启动）action=${intent?.action}")

        // 前台服务通道的硬性要求：5 秒内必须 startForeground，否则系统终止进程。
        runCatching {
            startForeground(AdbPairingFlow.FOREGROUND_ID, AdbPairingFlow.foregroundNotification(this))
        }.onFailure { Log.w(TAG, "startForeground failed", it) }

        // 与 MainActivity 相同的转发：解析 RemoteInput 结果 → 执行配对
        AdbPairingFlow.handleIntent(this, intent)

        // 配对是异步协程（SPAKE2 + TLS 握手通常数秒）。这里不能立刻 stopSelf：
        // Service 一停进程就降级，协程可能被杀 → 又是「无反应」。
        // 保持前台存活一段时间兜住整个配对窗口，之后自动清理。
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
            stopSelf(startId)
        }, KEEP_ALIVE_MS)

        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "PairingReplyService"

        /** 前台服务保活时长：覆盖 SPAKE2+TLS 配对与自动连接。 */
        private const val KEEP_ALIVE_MS = 30_000L
    }
}
