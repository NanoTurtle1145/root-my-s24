package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
 */
class PairingReplyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ${intent?.action}")
        // 与 MainActivity 相同的转发：解析 RemoteInput 结果 → 执行配对
        AdbPairingFlow.handleIntent(this, intent)
        // 一次性处理，处理完即停止，不留常驻 Service
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "PairingReplyService"
    }
}
