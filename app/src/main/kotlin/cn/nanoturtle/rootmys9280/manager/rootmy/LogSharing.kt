package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context

/**
 * 运行日志共享方式。首次启动时询问，之后可在「设置 → 隐私」里随时修改。
 *
 * 三个选项对应三种上传时机：
 * - [ALWAYS]：每次运行结束后自动上传，不需要用户确认。
 * - [MANUAL]：运行结束后弹一次提示，由用户决定这一次传不传。
 * - [NEVER]：只留在本机，绝不联网。
 */
enum class LogSharing {
    ALWAYS,
    MANUAL,
    NEVER,
    ;

    companion object {
        /** 存量安装（升级上来的老用户）默认手动，避免在用户没做过选择时静默上传。 */
        val DEFAULT = MANUAL

        fun parse(raw: String?): LogSharing =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/**
 * 首次启动引导（必读指南 / 通知权限 / 日志共享）的持久化状态。
 *
 * 全部存在 `SharedPreferences("settings")` 里，与应用其余偏好同一个文件，
 * 这样清数据/备份的行为与其它设置一致。
 */
object OnboardingPrefs {

    private const val PREFS = "settings"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_WIKI_ACCEPTED = "wiki_accepted"
    private const val KEY_LOG_SHARING = "log_sharing"
    private const val KEY_NOTIF_ASKED = "notif_permission_asked"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 引导是否已完成（完成过就不再自动弹出）。 */
    fun isDone(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    /** 用户是否已确认读过必读指南（关于页/设置页据此决定是否再提示）。 */
    fun isWikiAccepted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIKI_ACCEPTED, false)

    fun markWikiAccepted(context: Context) {
        prefs(context).edit().putBoolean(KEY_WIKI_ACCEPTED, true).apply()
    }

    fun logSharing(context: Context): LogSharing =
        LogSharing.parse(prefs(context).getString(KEY_LOG_SHARING, null))

    fun setLogSharing(context: Context, mode: LogSharing) {
        prefs(context).edit().putString(KEY_LOG_SHARING, mode.name).apply()
    }

    /** 通知权限是否已经问过一次（系统弹窗只弹一次，问过就不再自动拉起）。 */
    fun isNotificationAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIF_ASKED, false)

    fun markNotificationAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
    }
}
