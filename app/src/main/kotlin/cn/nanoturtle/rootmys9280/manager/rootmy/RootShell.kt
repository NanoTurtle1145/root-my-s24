package cn.nanoturtle.rootmys9280.manager.rootmy

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * App 进程内直接调用 root 执行器（移植自 RootExec GUI 的 Shell，见 /home/nt/项目/rootexec-gui）。
 *
 * 通道优先级：
 *  1. /system/bin/su、/system/xbin/su —— KernelSU / Magisk 的 sucompat；
 *     必须已在 KSU/Magisk 管理器里给本 App 授权 root，否则失败（或弹授权框）。
 *  2. 43499 临时 root 的 helper（用 -c 传命令）—— KSU late-load 之后这条会失效：
 *     /data/adb 被收保护，helper 转调 KSU 的 su，而 ksud daemon 不在 → su: connect daemon。
 *
 * 与 ShellExecutor（Shizuku / ADB 无线，uid 2000 shell）的区别：这里跑在 root 域，
 * 所以 setenforce / 写 /sys/fs/selinux/enforce 这类操作才真的生效。
 */
object RootShell {
    private val SU_CANDIDATES = listOf("/system/bin/su", "/system/xbin/su")

    /** 当前可用的 su 路径（KernelSU sucompat 注入的 /system/bin/su 对已授权 App 可见）。 */
    fun suPath(): String? = SU_CANDIDATES.firstOrNull { File(it).exists() }

    /** 执行 root 命令，返回 (退出码, 合并输出)；超过 timeoutSec 强杀并返回 -1。 */
    fun run(command: String, fallbackHelper: String? = null, timeoutSec: Long = 8): Pair<Int, String> {
        val attempts = mutableListOf<List<String>>()
        suPath()?.let { attempts.add(listOf(it, "-c", command)) }
        if (fallbackHelper != null && File(fallbackHelper).exists()) {
            attempts.add(listOf(fallbackHelper, "-c", command))
        }
        if (attempts.isEmpty()) return -1 to "没有可用的 root 通道（su 不存在，helper 也不在）"

        var last = -1 to "执行失败"
        for (argv in attempts) {
            try {
                val p = ProcessBuilder(argv).redirectErrorStream(true).start()
                val out = StringBuilder()
                val reader = Thread {
                    try {
                        p.inputStream.bufferedReader().useLines { seq ->
                            for (line in seq) {
                                if (out.length < 32768) out.append(line).append("\n")
                            }
                        }
                    } catch (_: Throwable) {}
                }
                reader.start()
                val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    p.destroyForcibly()
                    reader.join(500)
                    return -1 to "超时（" + timeoutSec + "s）"
                }
                reader.join(500)
                val code = p.exitValue()
                last = code to out.toString().trim()
                if (code == 0) return last
            } catch (t: Throwable) {
                last = -1 to (t.message ?: t.toString())
            }
        }
        return last
    }

    /** 切换 SELinux：permissive=true → setenforce 0；false → setenforce 1。 */
    fun setSelinux(permissive: Boolean, fallbackHelper: String? = null): Pair<Int, String> =
        run("setenforce " + (if (permissive) 0 else 1) + "; getenforce", fallbackHelper)
}
