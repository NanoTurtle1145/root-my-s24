package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shizuku 控制器：通过 Shizuku 以 shell 权限执行命令。
 * 使用前提：手机已安装并启动 Shizuku（无线/有线 ADB 授权）。
 */
object ShizukuController : ShellExecutor {
    private const val PERMISSION_REQUEST_CODE = 0x5352

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    suspend fun pingUntilRunning(timeoutMillis: Long = 5_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isRunning()) return true
            delay(100)
        }
        return isRunning()
    }

    fun isGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        if (!isRunning()) return false
        return suspendCancellableCoroutine { continuation ->
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (error: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resumeWithException(error)
            }
        }
    }

    // ---- ShellExecutor ----

    override fun isReady(): Boolean = isGranted()

    /** 以 shell 权限执行命令，返回进程。 */
    override fun exec(cmd: Array<String>, env: Array<String>?, dir: String?): Process {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder unavailable")
        return RemoteProcess(IShizukuService.Stub.asInterface(binder).newProcess(cmd, env, dir))
    }

    /** 执行单条命令，返回合并输出（失败时返回空串）。 */
    override fun capture(cmd: Array<String>): String {
        val process = exec(cmd)
        return try {
            val out = readAllStreams(process)
            if (process.waitFor() == 0) out else ""
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    /** 执行 shell 脚本，返回 (退出码, 输出)。 */
    override fun shell(cmd: String): Pair<Int, String> {
        val process = exec(arrayOf("/system/bin/sh", "-c", cmd))
        return try {
            val out = readAllStreams(process)
            process.waitFor() to out
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    /**
     * 并发读取 stdout + stderr，避免单流阻塞死锁：
     * 若命令先写满 stderr 管道缓冲（64KB）再写 stdout，顺序读取会在
     * stdout 的 readText() 处永久阻塞（stderr 无人消费 → 进程不退出）。
     * 两个线程同时读两条流可彻底规避。
     */
    private fun readAllStreams(process: Process): String {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val t1 = Thread {
            try {
                process.inputStream.bufferedReader().use { stdout.append(it.readText()) }
            } catch (_: Throwable) {
            }
        }
        val t2 = Thread {
            try {
                process.errorStream.bufferedReader().use { stderr.append(it.readText()) }
            } catch (_: Throwable) {
            }
        }
        t1.start()
        t2.start()
        try {
            t1.join()
            t2.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return stdout.toString() + stderr.toString()
    }

    override fun writeFile(remotePath: String, mode: String, source: InputStream) {
        val process = exec(
            arrayOf(
                "sh", "-c",
                "rm -f '$remotePath' 2>/dev/null; cat > '$remotePath' && chmod $mode '$remotePath'"
            )
        )
        val err = StringBuilder()
        val exitCode = try {
            process.outputStream.use { output ->
                source.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            // 收集 stderr 用于诊断
            val errorReader = process.errorStream.bufferedReader()
            Thread {
                errorReader.forEachLine { err.append(it).append('\n') }
            }.start()
            process.waitFor()
        } finally {
            if (process.isAlive) process.destroy()
        }
        check(exitCode == 0) {
            "Failed to stage $remotePath (exit $exitCode)${if (err.isNotBlank()) ": ${err}" else ""}"
        }
    }

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream()) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream()) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()
        override fun exitValue(): Int = remote.exitValue()

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = remote.alive()
    }
}
