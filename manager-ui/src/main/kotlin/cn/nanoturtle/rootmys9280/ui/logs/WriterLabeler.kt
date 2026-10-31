package cn.nanoturtle.rootmys9280.ui.logs

import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Names the process behind a uid, so a writer chip is a name rather than a number.
 *
 * Adapted from Vector's `WriterLabeler`: both managers read logs written by installed packages, and
 * neither should invent its own answer for what a uid is called.
 *
 * One package holding the uid is named by its label; a uid several packages share is named by the
 * platform instead, so that a chip is a name rather than a list. The platform only names what it has
 * a package setting for, and the uids that matter most in a root tool's log have none: root writes
 * as 0 and is named by nothing at all, while the system's shared id comes back spelled as the
 * setting that holds it. Those few are spelled out here, from the platform's own fixed assignments,
 * so the log of a daemon running as root does not read as a column of numbers. Anything still
 * unnamed is left to the screen, which shows the number — and a number still separates one process
 * from another.
 *
 * A uid outside the first user carries the user it belongs to, since the same application in a
 * second profile is a different writer with the same label and would otherwise appear twice with
 * nothing to tell the two apart.
 */
class WriterLabeler(private val packageManager: PackageManager) {

    /**
     * Answers and refusals alike, because both cost the same to find out.
     *
     * Names are asked once per scan rather than once per frame, and a device-wide log carries dozens
     * of uids the platform will never name; re-asking for each of them every time would be binder
     * traffic that can only ever produce the same silence. The price is that a name only learnable
     * later — an app installed while the screen is open — waits for the next process.
     */
    private val cache = ConcurrentHashMap<Int, String>()

    fun label(uid: Int): String? = cache.getOrPut(uid) { withUser(uid, resolve(uid)) }.takeIf { it.isNotEmpty() }

    private fun resolve(uid: Int): String {
        WELL_KNOWN[uid % PER_USER_RANGE]?.let { return it }
        val packages = runCatching { packageManager.getPackagesForUid(uid) }.getOrNull().orEmpty()
        if (packages.size == 1) {
            val label =
                runCatching { packageManager.getApplicationInfo(packages[0], 0).loadLabel(packageManager).toString() }
                    .getOrNull()
            return label?.trim().orEmpty()
        }
        // A uid several packages share has no single name of its own; the platform's own name for it
        // (`u0_a123` and friends) is the honest answer, so it comes back empty and the number shows.
        return ""
    }

    private fun withUser(uid: Int, label: String): String {
        if (label.isEmpty()) return label
        val user = uid / PER_USER_RANGE
        return if (user == 0) label else "$label (u$user)"
    }

    private companion object {
        private const val PER_USER_RANGE = 100000

        /** The fixed uids Android assigns, from `system/core/include/private/android_filesystem_config.h`. */
        private val WELL_KNOWN =
            mapOf(
                0 to "root",
                1000 to "system",
                1001 to "radio",
                1002 to "bluetooth",
                1003 to "graphics",
                1004 to "input",
                1006 to "drm",
                1007 to "log",
                1009 to "mount",
                1010 to "wifi",
                1011 to "adb",
                1013 to "media",
                1016 to "keystore",
                1017 to "nfc",
                1021 to "gps",
                1023 to "sdcard_rw",
                1024 to "media_rw",
                1026 to "drmrpc",
                1027 to "sdcard_r",
                1028 to "clat",
                1029 to "net_bt_admin",
                1030 to "net_bt",
                1031 to "net_bw_stats",
                1032 to "net_bw_acct",
                1033 to "net_bt_stack",
                1034 to "readproc",
                1035 to "wakelock",
                1036 to "uunwa",
                1039 to "media_drm",
                1041 to "audioserver",
                1046 to "camera",
                1047 to "logd",
                1049 to "lmkd",
                1050 to "tombstoned",
                1051 to "storaged",
                1052 to "statsd",
                1066 to "webview_zygote",
                1067 to "incidentd",
                1069 to "gpu_service",
                1071 to "update_engine",
                1073 to "network_stack",
                1077 to "traced_probes",
                1080 to "gpu",
                1083 to "nnapi",
            )
    }
}
