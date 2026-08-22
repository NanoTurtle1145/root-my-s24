package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 无线调试通知配对流程：弹出通知 → 输入配对码 → 自动配对 → 自动连接。
 *
 * 交互流程（与 Shizuku 一致，但寄生式架构下用 Activity 而非 Service 接收 RemoteInput）：
 * 1. 用户点授权卡片的「通知配对」按钮
 * 2. mDNS 搜索 `_adb-tls-pairing._tcp`（本机配对端口），发「搜索中」通知
 * 3. 找到端口 → 更新通知，带 RemoteInput action（通知上输入 6 位配对码）
 * 4. 用户输入 → PendingIntent 送 MainActivity → 回调本 flow → 配对执行
 * 5. 配对成功 → 自动 mDNS 发现连接端口并 connect（39xxx）
 * 6. 结果通知（成功/失败）
 *
 * 所有通知基于同一个 ID 更新，避免通知栏堆积。
 */
object AdbPairingFlow {

    private const val TAG = "AdbPairingFlow"
    private const val NOTIFICATION_ID = 2
    private const val CHANNEL_ID = "adb_pairing"
    private const val REPLY_REQUEST = 102
    private const val STOP_REQUEST = 103
    private const val KEY_REMOTE_INPUT = "pairing_code"
    private const val EXTRA_PORT = "adb_pair_port"
    private const val ACTION_PAIR_REPLY = "cn.nanoturtle.ADB_PAIR_REPLY"
    private const val ACTION_STOP_SEARCH = "cn.nanoturtle.ADB_PAIR_STOP"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mdnsPair: AdbMdns? = null
    private var mdnsConnect: AdbMdns? = null
    private var pairPort = -1
    private var connectHost = ""
    private var connectPort = -1
    private var searching = false
    private var paired = false

    // ---- 对外 API ----

    /**
     * 开始通知配对流程：创建通知渠道 → mDNS 搜索配对端口 → 发通知。
     * 由 AuthCard 的「通知配对」按钮触发。
     * 无线调试的 mDNS 服务是 Android 11+（API 30）功能，旧系统回退手动配对。
     * @return null=已开始；非 null=失败原因（如通知权限未开启）
     */
    fun startSearch(context: Context): String? {
        if (searching) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Android 11+ required"
        // Android 13+：POST_NOTIFICATIONS 未授予时 notify() 会静默丢弃，
        // 必须显式检查并引导用户去系统设置开启。
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return context.getString(R.string.notification_adb_pairing_no_permission)
        }
        searching = true
        paired = false
        pairPort = -1
        connectPort = -1

        createChannel(context)
        notifySearching(context)

        mdnsPair = AdbMdns(context, AdbMdns.TLS_PAIRING) { host, port ->
            if (port <= 0) return@AdbMdns
            pairPort = port
            connectHost = host
            notifyInputCode(context, port)
        }
        mdnsPair?.start()
        return null
    }

    /** 通知配对是否可用（Android 11+）。 */
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 停止搜索并移除通知。 */
    fun stopSearch(context: Context) {
        if (!searching) return
        searching = false
        mdnsPair?.stop()
        mdnsConnect?.stop()
        mdnsPair = null
        mdnsConnect = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * 由 MainActivity 在收到 RemoteInput 结果时调用。
     * 用户在通知上输入配对码后，系统把结果通过 PendingIntent 送到 MainActivity，
     * MainActivity 读取后转发到这里。
     */
    fun onPairCodeReceived(context: Context, code: String, port: Int) {
        if (!searching && port <= 0) return
        val actualPort = if (port > 0) port else pairPort
        if (actualPort <= 0) return
        if (code.isBlank()) return

        notifyWorking(context)
        searching = false
        mdnsPair?.stop()

        scope.launch {
            val key = AdbWirelessController.getAdbKey()
            if (key == null) {
                notifyResult(context, false, "key not initialized")
                return@launch
            }
            val success = try {
                AdbPairingClient(connectHost.ifBlank { "127.0.0.1" }, actualPort, code.trim(), key).use { client ->
                    client.start()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "pair failed", t)
                false
            }
            if (success) {
                paired = true
                notifyResult(context, true, null)
                // 配对成功 → 自动发现连接端口并连接
                findAndConnect(context)
            } else {
                notifyResult(context, false, "pairing code is wrong")
            }
        }
    }

    /** 配对成功后自动发现连接端口 */
    private fun findAndConnect(context: Context) {
        mdnsConnect = AdbMdns(context, AdbMdns.TLS_CONNECT) { host, port ->
            if (port <= 0) return@AdbMdns
            mdnsConnect?.stop()
            mdnsConnect = null
            scope.launch {
                AdbWirelessController.connect(host, port)
                notifyResult(context, AdbWirelessController.isConnected(), null)
            }
        }
        mdnsConnect?.start()
    }

    // ---- 通知 ----

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_adb_pairing),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notifySearching(context: Context) {
        try {
            val stopIntent = Intent(context, MainActivity::class.java)
                .setAction(ACTION_STOP_SEARCH)
            val stopPi = PendingIntent.getActivity(
                context, STOP_REQUEST, stopIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                else PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle(context.getString(R.string.notification_adb_pairing_searching_title))
                .setContentText(context.getString(R.string.notification_adb_pairing_searching_text))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notification_adb_pairing_stop), stopPi)
                .setOngoing(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.w(TAG, "notifySearching failed", e)
        }
    }

    private fun notifyInputCode(context: Context, port: Int) {
        try {
            val remoteInput = RemoteInput.Builder(KEY_REMOTE_INPUT)
                .setLabel(context.getString(R.string.notification_adb_pairing_input_hint))
                .build()
            val replyIntent = Intent(context, MainActivity::class.java)
                .setAction(ACTION_PAIR_REPLY)
                .putExtra(EXTRA_PORT, port)
            val replyPi = PendingIntent.getActivity(
                context, REPLY_REQUEST, replyIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                else PendingIntent.FLAG_UPDATE_CURRENT
            )
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_edit,
                context.getString(R.string.notification_adb_pairing_input_action),
                replyPi
            ).addRemoteInput(remoteInput).build()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle(context.getString(R.string.notification_adb_pairing_service_found_title))
                .setContentText(context.getString(R.string.notification_adb_pairing_service_found_text, port))
                .addAction(replyAction)
                .setOngoing(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.w(TAG, "notifyInputCode failed", e)
        }
    }

    private fun notifyWorking(context: Context) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle(context.getString(R.string.notification_adb_pairing_working_title))
                .setContentText(context.getString(R.string.notification_adb_pairing_working_text))
                .setOngoing(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.w(TAG, "notifyWorking failed", e)
        }
    }

    private fun notifyResult(context: Context, success: Boolean, errorText: String?) {
        try {
            val title = if (success) {
                context.getString(R.string.notification_adb_pairing_success_title)
            } else {
                context.getString(R.string.notification_adb_pairing_failed_title)
            }
            val text = when {
                success -> {
                    val connected = AdbWirelessController.isConnected()
                    if (connected) {
                        context.getString(R.string.notification_adb_pairing_success_connected_text)
                    } else {
                        context.getString(R.string.notification_adb_pairing_success_text)
                    }
                }
                errorText?.contains("pairing code", ignoreCase = true) == true ->
                    context.getString(R.string.notification_adb_pairing_code_wrong)
                else -> errorText
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(!success)
                .setAutoCancel(success)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            if (success) {
                // 用完后清理
                stopSearch(context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "notifyResult failed", e)
        }
    }

    // ---- 供外部查询 ----

    fun isSearching(): Boolean = searching
    fun isPaired(): Boolean = paired
    fun getActionPairReply(): String = ACTION_PAIR_REPLY
    fun getActionStopSearch(): String = ACTION_STOP_SEARCH
    fun getExtraPort(): String = EXTRA_PORT
    fun getRemoteInputKey(): String = KEY_REMOTE_INPUT

    /** 处理 MainActivity 传入的 Intent。由 MainActivity 在 onCreate/onNewIntent 调用。 */
    fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_PAIR_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(KEY_REMOTE_INPUT)?.toString()
                if (!code.isNullOrBlank()) {
                    val port = intent.getIntExtra(EXTRA_PORT, -1)
                    onPairCodeReceived(context, code, port)
                }
            }
            ACTION_STOP_SEARCH -> stopSearch(context)
        }
    }
}