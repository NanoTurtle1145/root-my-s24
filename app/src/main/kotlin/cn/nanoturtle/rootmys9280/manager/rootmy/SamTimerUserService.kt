package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shizuku UserService：在 shell 进程里跑 CVE-2026-98260（posix-cpu-timers UAF）链路。
 *
 * 与之前用 shellExecutor.exec() 从 App 进程起的差别（也是原版 SamTimer 的做法）：
 * UserService 是一个**常驻进程**，exploit 是它的子进程；App 切到后台/打开管理器
 * 不会带走它，parked 的 libtimer 不会因为会话回收而被杀（那会导致内核页提前释放、
 * 过一会卡死/重启）。
 *
 * 流程（照搬 fork v1.3 的 RootService）：
 *  1. 暂存 libtimer / uhexec / umh_action.sh / ksud(32601) 到 /data/local/tmp
 *  2. 抬高 tracefs buffer
 *  3. libtimer umhpipe：timer UAF → pipe 读写 → 伪造 workqueue → UMH → uhexec
 *     → bind-mount KDP 补丁版 ksud → late-load（KSU 在这一步自己起来）
 *  4. 成功后 exploit 保持 parked（它拿着页，不能强杀）；重启是唯一清理方式
 */
class SamTimerUserService(private val context: Context) : IRootService.Stub() {

    private val running = AtomicBoolean(false)

    /** 用哪份 ksud 做 late-load：3.3.0(32601 多 KMI，会重启管理器) / 3.2.5(原版，行为最稳)。 */
    @Volatile private var ksudAssetName: String = ASSET_KSUD_32601

    /** issue #35：98260 路径也要支持「禁用 KSU 模块」——由 action 脚本在 root 上下文执行。 */
    @Volatile private var disableModules: Boolean = false

    /** 设备构建号；有 samtimer/targets/<tag>/ 下的专属载荷时优先用它（issue #40 约定）。 */
    @Volatile private var targetTag: String = ""

    companion object {
        private const val WORK_DIR = "/data/local/tmp/w"
        private const val KSUD_STAGE_PATH = "/data/local/tmp/.ksud-stage"
        private const val TRACE_BUF = "/sys/kernel/tracing/buffer_size_kb"
        private const val WAIT_SEC = 300L

        private const val ASSET_PREFIX = "samtimer/"
        private const val ASSET_LIBTIMER = ASSET_PREFIX + "libtimer"
        private const val ASSET_UHEXEC = ASSET_PREFIX + "uhexec"
        private const val ASSET_ACTION = ASSET_PREFIX + "umh_action.sh"           // 3.2.5: --ephemeral
        private const val ASSET_ACTION_STOCK = ASSET_PREFIX + "umh_action-stock.sh" // 3.3.0: stock late-load
        private const val ASSET_KSUD_32601 = ASSET_PREFIX + "ksud-32601"
        private const val ASSET_KSUD_32525 = ASSET_PREFIX + "ksud-32525"
    }

    override fun isReady(): Boolean {
        val uid = Process.myUid()
        return uid == 2000 || uid == 0
    }

    override fun isRooted(): Boolean = moduleLive()

    override fun destroy() {}

    override fun setKsudAsset(name: String) {
        if (name == ASSET_KSUD_32601 || name == ASSET_KSUD_32525) {
            ksudAssetName = name
        }
    }

    private fun moduleLive(): Boolean = try {
        File("/proc/modules").useLines { lines ->
            lines.any { it.startsWith("kernelsu ") }
        }
    } catch (_: Exception) {
        false
    }

    override fun setTargetTag(tag: String) {
        targetTag = tag.trim().uppercase()
    }

    override fun setDisableModules(disable: Boolean) {
        disableModules = disable
    }

    override fun runExploit(callback: IRootCallback?) {
        if (callback == null) return
        if (!running.compareAndSet(false, true)) {
            callback.onLog("error: another run is already in progress")
            callback.onComplete(1, false)
            return
        }
        Thread({
            var rc = 1
            var ok = false
            try {
                rc = runChain(callback)
                ok = rc == 0
            } catch (e: Exception) {
                runCatching { callback.onLog("error: ${e.message}") }
            } finally {
                runCatching { callback.onComplete(rc, ok) }
                running.set(false)
            }
        }, "samtimer-runner").start()
    }

    private fun log(callback: IRootCallback, line: String) {
        // 先落盘再回调：late-load 阶段如果内核直接挂掉/重启，App 来不及上传日志，
        // /data/local/tmp/w/service.log 是重启后唯一能拿到的现场（/data/local/tmp 不清）。
        runCatching {
            java.io.File(WORK_DIR, "service.log").appendText(line + "\n")
        }
        runCatching { callback.onLog(line) }
    }

    /**
     * 打开一个资产：优先用 samtimer/targets/<TAG>/<basename>（按构建号专属载荷），
     * 不存在再回退到 samtimer/<basename>。12260 target 目录结构见 issue #40。
     */
    private fun openAsset(name: String): java.io.InputStream {
        val base = name.substringAfterLast('/')
        val tag = targetTag
        if (tag.isNotEmpty() && (base == "libtimer" || base == "uhexec" || base == "ksud" || base.startsWith("ksud-"))) {
            val override = "samtimer/targets/$tag/$base"
            try {
                return context.assets.open(override)
            } catch (_: java.io.FileNotFoundException) {
                // fall through to shared asset
            }
        }
        return context.assets.open(name)
    }

    private fun stageAsset(name: String, dest: File): Boolean = try {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        openAsset(name).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setReadable(true, false)
        dest.setExecutable(true, false)
        true
    } catch (_: Exception) {
        false
    }

    private fun runChain(callback: IRootCallback): Int {
        val uid = Process.myUid()
        if (uid != 2000 && uid != 0) {
            log(callback, "error: not running as shell/root (uid=$uid)")
            return 1
        }
        log(callback, "shell context ok (uid=$uid)")

        if (moduleLive()) {
            log(callback, "KernelSU module already live")
            return 0
        }

        val workDir = File(WORK_DIR)
        if (!(workDir.isDirectory || workDir.mkdirs())) {
            log(callback, "error: cannot create $WORK_DIR")
            return 1
        }
        val timerBin = File(workDir, "libtimer")
        val uhexec = File(workDir, "uhexec")
        val action = File(workDir, "umh_action.sh")
        val ksud = File("/data/local/tmp/ksud-s25u-kdp")
        val stage = File(KSUD_STAGE_PATH)

        // 3.3.0 的 stock ksud 不认 --ephemeral（会当成未知参数/或跑完整流程），
        // 所以按选择的驱动切换 action 脚本：3.2.5 用 ephemeral 版，3.3.0 用原版。
        val actionAsset =
            if (ksudAssetName.endsWith("32601")) ASSET_ACTION_STOCK else ASSET_ACTION
        if (!stageAsset(ASSET_LIBTIMER, timerBin) ||
            !stageAsset(ASSET_UHEXEC, uhexec) ||
            !stageAsset(actionAsset, action) ||
            !stageAsset(ksudAssetName, ksud) ||
            !stageAsset(ksudAssetName, stage)
        ) {
            log(callback, "error: failed to stage embedded files")
            return 1
        }
        log(callback, "staged libtimer=${timerBin.length()} uhexec=${uhexec.length()} action=${action.length()} ksud=${ksud.length()}")
        log(callback, "staged $KSUD_STAGE_PATH")

        try {
            File(TRACE_BUF).writeText("65536\n")
            log(callback, "trace buffer set to 65536kB")
        } catch (e: Exception) {
            log(callback, "warn: trace buffer write failed (${e.message})")
        }

        // 把「禁用模块」意图传给 root 上下文里的 action 脚本：它能读写 /data/adb。
        runCatching {
            if (disableModules) {
                java.io.File(WORK_DIR, "disable-modules").writeText("1")
            } else {
                java.io.File(WORK_DIR, "disable-modules").delete()
            }
        }
        log(callback, if (disableModules) "disable-modules: on (action 会给每个模块写 disable 标记)" else "disable-modules: off")

        log(callback, "starting exploit (umhpipe, detached)...")
        // 关键：不要用 ProcessBuilder 直接当子进程。那样 libtimer 的父进程是 UserService，
        // UserService 又挂在 Shizuku/adbd（init service）下面 —— 一次软重启（stop/start）
        // 会把整条链杀掉，parked 页被释放，内核接着就硬重启。
        //
        // 这里用 setsid + sh 退出，让 libtimer 被 reparent 到 init(1)：init 不管它，
        // 软重启（zygote/adbd 重启）也不会碰它 —— 实测有些运行里 parked 进程正是这样活下来的。
        // 日志走 run.log，UserService 负责 tail；UserService 死了也没关系，exploit 还在。
        val logFile = File(workDir, "run.log")
        runCatching { logFile.delete() }
        val launcher = ProcessBuilder(
            "/system/bin/sh",
            "-c",
            "cd ${workDir.absolutePath} && setsid ./libtimer umhpipe > run.log 2>&1 < /dev/null & echo started",
        ).redirectErrorStream(true).start()
        runCatching { launcher.waitFor() }
        log(callback, "exploit detached (reparented to init)")

        val deadline = System.currentTimeMillis() + WAIT_SEC * 1000
        var tailed = 0L
        while (System.currentTimeMillis() < deadline) {
            // 增量 tail run.log：把 libtimer 输出转给 App（UserService 自己死了不影响 exploit）
            runCatching {
                if (logFile.isFile) {
                    val len = logFile.length()
                    if (len > tailed) {
                        java.io.RandomAccessFile(logFile, "r").use { rf ->
                            rf.seek(tailed)
                            val buf = ByteArray((len - tailed).toInt().coerceAtMost(64 * 1024))
                            val n = rf.read(buf)
                            if (n > 0) {
                                tailed += n
                                buf.copyOf(n).toString(Charsets.UTF_8)
                                    .lineSequence().filter { it.isNotBlank() }
                                    .forEach { log(callback, it) }
                            }
                        }
                    }
                }
            }
            if (moduleLive()) {
                log(callback, "KERNELSU_LIVE")
                log(callback, "success; exploit left parked (do not force-stop)")
                return 0
            }
            Thread.sleep(1000)
        }

        // 不杀 exploit：它可能拿着伪造页和 zombie 链；杀掉可能 panic。重启是唯一清理方式。
        log(callback, "error: KernelSU did not appear within ${WAIT_SEC}s (exploit left parked)")
        return 4
    }
}
