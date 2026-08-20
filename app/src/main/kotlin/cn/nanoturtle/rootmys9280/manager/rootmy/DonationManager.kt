package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import android.content.SharedPreferences

/**
 * 捐赠提醒计数。
 *
 * 记录 root 成功次数，并在累计达到里程碑（10、25、50、75、100…）时提示一次捐赠。
 * 每个里程碑只提示一次，避免反复打扰；次数与已提示的里程碑都持久化保存。
 */
class DonationManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前累计的 root 成功次数。 */
    val successCount: Int
        get() = prefs.getInt(KEY_SUCCESS_COUNT, 0)

    /** 记录一次 root 成功；返回本次是否跨过了新的捐赠里程碑。 */
    fun recordSuccess(): Boolean {
        val count = successCount + 1
        prefs.edit().putInt(KEY_SUCCESS_COUNT, count).apply()
        val milestone = currentMilestone(count)
        if (milestone > 0 && !wasMilestoneNotified(milestone)) {
            prefs.edit().putInt(KEY_LAST_NOTIFIED_MILESTONE, milestone).apply()
            return true
        }
        return false
    }

    /** 当前达到的里程碑（0 表示未达到任何里程碑）。 */
    private fun currentMilestone(count: Int): Int {
        if (count >= 100) return 100
        if (count >= 75) return 75
        if (count >= 50) return 50
        if (count >= 25) return 25
        if (count >= 10) return 10
        return 0
    }

    private fun wasMilestoneNotified(milestone: Int): Boolean =
        prefs.getInt(KEY_LAST_NOTIFIED_MILESTONE, 0) >= milestone

    companion object {
        private const val PREFS_NAME = "donation"
        private const val KEY_SUCCESS_COUNT = "root_success_count"
        private const val KEY_LAST_NOTIFIED_MILESTONE = "last_notified_milestone"
    }
}
