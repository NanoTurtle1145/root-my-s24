package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 根流程 ViewModel：
 * 1. 检查/申请 Shizuku 权限
 * 2. 把 assets 里的载荷推到 /data/local/tmp
 * 3. LD_PRELOAD 触发 CVE-2026-43499
 * 4. 等待 root 标记
 * 5. KernelSU late-load
 *
 * 目标：SM-S9280 (国行) / S9280ZCS6DZF2 / kernel 6.1.145（修正版载荷）
 */
class RootViewModel(app: Application) : AndroidViewModel(app) {
    private val app: Application = app

    /** 一条日志（stage=所属阶段 0=阶段外, summary=是否为总结性标题行） */
    data class LogLine(val stage: Int, val text: String, val summary: Boolean = false)

    data class UiState(
        /** 完整日志（每行一条，跨 stage 累积，不再被新输出覆盖） */
        val logLines: List<LogLine> = emptyList(),
        /** 当前执行到的阶段（1..5，0=未开始） */
        val currentStage: Int = 0,
        val busy: Boolean = false,
        val rooted: Boolean = false,
        /** KNOX 状态展示文案（由 refreshKnox 填充） */
        val knoxState: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** exploit 进程的原始输出累积缓冲（跨轮询保留，用于计算增量） */
    private val captured = StringBuilder()
    /** 未以换行结尾的半行（下次追加时续上） */
    private var pendingPartial = ""
    /** 当前阶段（由 [n/5] 标题行驱动） */
    private var currentStage = 0

    private val payloadName = "cve-2026-43499"
    private val rootHelperName = "cve-2026-43499-root"
    private val ksudName = "ksud-selected"
    private val PREFS_SETTINGS = "settings"

    private val tmpPayload = "/data/local/tmp/$payloadName"
    private val tmpRootHelper = "/data/local/tmp/$rootHelperName"
    private val tmpKsud = "/data/local/tmp/$ksudName"

    fun start() {
        if (_state.value.busy) return
        // 新一轮运行：重置增量缓冲（日志历史保留，可手动清空）
        captured.clear()
        pendingPartial = ""
        currentStage = 0
        _state.value = _state.value.copy(busy = true, rooted = false, currentStage = 0)
        viewModelScope.launch {
            try {
                runRootFlow()
            } catch (t: Throwable) {
                appendLog("✗ 失败: ${t.message}")
            } finally {
                // 唤醒屏幕（如果运行期间自动熄屏了）
                if (autoScreenOff) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ShizukuController.shell("input keyevent 26")
                    }
                    appendLog("◆ 已唤醒屏幕")
                }
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** 设置页的"运行期间自动熄屏"开关（Shizuku 运行，读 prefs 实时生效） */
    fun setAutoScreenOff(enabled: Boolean) {
        app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("auto_screen_off", enabled).apply()
    }

    val autoScreenOff: Boolean
        get() = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .getBoolean("auto_screen_off", true)

    fun clearLog() {
        captured.clear()
        pendingPartial = ""
        currentStage = 0
        _state.value = _state.value.copy(logLines = emptyList(), currentStage = 0)
    }

    /** 供 UI 追加提示行（如导出结果），不改变阶段 */
    fun notify(msg: String) {
        appendLog(msg)
    }

    /**
     * 读取 KNOX 状态（经 Shizuku 读只读属性，不影响熔断判断）。
     * 注意：本流程不熔断 KNOX；warranty_bit=0 为完好。
     */
    suspend fun refreshKnox() {
        val bit = runCatching {
            ShizukuController.capture(
                arrayOf("/system/bin/sh", "-c", "getprop ro.boot.warranty_bit 2>&1")
            ).trim()
        }.getOrDefault("")
        val vbs = runCatching {
            ShizukuController.capture(
                arrayOf("/system/bin/sh", "-c", "getprop ro.boot.verifiedbootstate 2>&1")
            ).trim()
        }.getOrDefault("")
        val text = when {
            bit == "1" -> "已触发 (warranty_bit=1)"
            bit == "0" && vbs == "green" -> "完好 (warranty_bit=0, verifiedbootstate=green)"
            bit == "0" -> "完好 (warranty_bit=0, verifiedbootstate=${vbs.ifBlank { "?" }})"
            vbs.isNotBlank() -> "未知 (verifiedbootstate=$vbs)"
            else -> "未知（需 Shizuku）"
        }
        _state.value = _state.value.copy(knoxState = text)
    }

    private suspend fun runRootFlow() = withContext(Dispatchers.IO) {
        appendLog("◆ RootMyS9280 · SM-S9280 DZF2 免解锁 root")
        appendLog("◆ 载荷: $payloadName (修正版, kmalloc_caches 0x176cbb8)")

        // 1. Shizuku
        appendLog("[1/5] 检查 Shizuku...")
        if (!ShizukuController.pingUntilRunning()) {
            throw IllegalStateException("Shizuku 未运行。请先启动 Shizuku（无线/有线 ADB 授权）")
        }
        if (!ShizukuController.requestPermission()) {
            throw IllegalStateException("Shizuku 权限被拒绝")
        }
        appendLog("✔ Shizuku 就绪")

        // 2. 推送载荷
        appendLog("[2/5] 推送载荷到 /data/local/tmp ...")
        extractAsset(payloadName)
        extractAsset(rootHelperName)
        extractAsset(ksudName)
        val stagedPayload = copyToTmp(payloadName, tmpPayload, "755")
        val stagedHelper = copyToTmp(rootHelperName, tmpRootHelper, "755")
        copyToTmp(ksudName, tmpKsud, "755")
        appendLog("✔ 推送完成 ($payloadName=${stagedPayload.length()}B, $rootHelperName=${stagedHelper.length()}B)")

        // 2.5 诊断：管道限制（F_SETPIPE_SZ EPERM 的根因排查）
        val pipeMax = ShizukuController.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/sys/fs/pipe-max-size 2>&1")
        ).trim()
        val pipeUser = ShizukuController.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/sys/fs/pipe-user-pages-soft 2>&1 || cat /proc/sys/fs/pipe-user-pages-hard 2>&1 || echo n/a")
        ).trim()
        val uname = ShizukuController.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/version 2>&1 | head -c 200")
        ).trim()
        appendLog("◆ 诊断: pipe-max-size=${pipeMax.ifBlank { "?" }} pipe-user-pages=${pipeUser.ifBlank { "?" }}")
        appendLog("◆ 诊断: /proc/version=${uname.ifBlank { "?" }}")
        if (pipeMax.isNotBlank()) {
            val maxKb = pipeMax.toLongOrNull() ?: 0
            if (maxKb > 0 && maxKb < 131072) {
                appendLog("⚠ pipe-max-size (${maxKb}B) < 128KB (exploit 需要 32 槽=128KB)，F_SETPIPE_SZ 会 EPERM")
            }
        }

        // 2.6 清理残留：上次失败的 exploit 子进程/守护进程会残留并污染 uid 2000 的
        //     pipe_bufs 配额，导致 F_SETPIPE_SZ EPERM（16/16 失败根因之一）
        appendLog("[2.6/5] 清理残留 exploit 进程与临时文件 ...")
        val cleanup = ShizukuController.shell(
            "pkill -9 -f 'cve-2026-43499' 2>/dev/null; " +
                "pkill -9 -f 'cve43499' 2>/dev/null; " +
                "rm -f /data/local/tmp/temp_su.sock /data/local/tmp/ksud-s25u-kdp /data/local/tmp/.ksud-stage; echo ok"
        )
        appendLog("✔ 环境清理完成 (exit=${cleanup.first})")

        // 3. 触发 exploit
        appendLog("[3/5] 触发 CVE-2026-43499 (LD_PRELOAD /system/bin/true) ...")
        // 运行期间自动熄屏：显示驱动停止 → 消除最大崩溃源（worklist 竞态）
        if (autoScreenOff) {
            ShizukuController.shell("input keyevent 26")
            appendLog("◆ 已自动熄屏（运行完成后将唤醒；可手动按电源键随时查看）")
        }
        val env = arrayOf(
            "EXPLOIT_ATTEMPTS=30",
            "P0_ATTEMPT_TIMEOUT_SEC=45",
            "EXPLOIT_ATTEMPT_TIMEOUT_SEC=120",
            "CVE43499_ROOT_HELPER=$tmpRootHelper",
            "LD_PRELOAD=$tmpPayload",
        )
        val process = ShizukuController.exec(
            arrayOf("/system/bin/sh", "-c", "true"),
            env,
        )
        val startedAt = SystemClock.elapsedRealtime()
        var exploitCompleted = false
        var rootObtained = false
        while (process.isAlive) {
            val delta = drainProcessOutput(process)
            if (delta.isNotEmpty()) {
                publishLog(delta)
                if (delta.contains("exploit completed")) exploitCompleted = true
                if (delta.contains("retval=0 socket=1") || delta.contains("done=1 root=1")) {
                    rootObtained = true
                }
            }
            if (SystemClock.elapsedRealtime() - startedAt > 15 * 60_000L) {
                process.destroy()
                throw IllegalStateException("exploit 超时（15 分钟）")
            }
            delay(500)
        }
        val finalLog = drainProcessOutput(process)
        publishLog(finalLog)
        if (finalLog.contains("exploit completed")) exploitCompleted = true
        if (finalLog.contains("retval=0 socket=1") || finalLog.contains("done=1 root=1")) {
            rootObtained = true
        }
        if (!exploitCompleted) {
            throw IllegalStateException("exploit 未完成（可能需要多试几次 / 重启后再试，概率性成功）")
        }
        if (!rootObtained) {
            throw IllegalStateException("exploit 完成但未拿到 root（标记缺失）")
        }
        appendLog("✔ 临时 root 已获得！")

        // 4. KernelSU late-load（经 root 守护进程 temp_su.sock 以 root 执行）
        appendLog("[4/5] KernelSU late-load (经 root 守护进程) ...")
        appendLog("◆ 提示: 运行期间建议熄屏（减少显示驱动 work，降低内核竞态概率）")
        // ksud 必须放在守护进程硬编码的加载路径 (su_daemon 的 KSU_LOADER_PATH)
        val stageCmd = "cp $tmpKsud /data/local/tmp/ksud-s25u-kdp && " +
            "cp $tmpKsud /data/local/tmp/.ksud-stage && " +
            "chmod 755 /data/local/tmp/ksud-s25u-kdp /data/local/tmp/.ksud-stage"
        val stageResult = ShizukuController.shell(stageCmd)
        if (stageResult.first != 0) {
            throw IllegalStateException("ksud 暂存失败 (exit=${stageResult.first}): ${stageResult.second.trim().takeLast(200)}")
        }
        appendLog("✔ ksud 已暂存到 ksud-s25u-kdp / .ksud-stage")
        // 以 su 客户端模式连接 root 守护进程，守护进程 fork root 子进程执行:
        //   ksud late-load --ephemeral --package-name me.weishu.kernelsu
        val ksu = ShizukuController.shell("$tmpRootHelper --late-load 2>&1")
        appendLog("ksud late-load: exit=${ksu.first}\n${ksu.second.trim().takeLast(300)}")
        if (ksu.first != 0) {
            throw IllegalStateException("KernelSU late-load 失败 (exit=${ksu.first})")
        }
        appendLog("✔ KernelSU 驱动已加载 (late-load exit=0)")

        // 5. 验证（late-load 内部已做 KSU 驱动 ioctl 校验；root 由 KernelSU 管理器提供）
        appendLog("[5/5] 验证...")
        val daemonCheck = ShizukuController.capture(
            arrayOf("/system/bin/sh", "-c", "ls -la /data/local/tmp/temp_su.sock 2>&1")
        )
        appendLog("root 守护进程: ${daemonCheck.trim()}")
        appendLog("✔ 请安装 KernelSU 管理器 (me.weishu.kernelsu v3.2.5, versionCode 32525) 后即可管理 root / 安装模块")

        _state.value = _state.value.copy(rooted = true)
        appendLog("🎉 Root 流程完成！")
    }

    private fun extractAsset(name: String): File {
        val out = File(app.filesDir, name)
        app.assets.open(name).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun copyToTmp(sourceName: String, target: String, mode: String): File {
        val src = File(app.filesDir, sourceName)
        ShizukuController.writeFile(target, mode, src.inputStream())
        return src
    }

    /** 只返回自上次调用以来新增的输出（累积缓冲在成员里） */
    private fun drainProcessOutput(process: Process): String {
        val before = captured.length
        try {
            val data = ByteArray(4096)
            while (process.inputStream.available() > 0) {
                val n = process.inputStream.read(data)
                if (n <= 0) break
                captured.append(String(data, 0, n, Charsets.UTF_8))
            }
            while (process.errorStream.available() > 0) {
                val n = process.errorStream.read(data)
                if (n <= 0) break
                captured.append(String(data, 0, n, Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }
        return if (captured.length > before) captured.substring(before) else ""
    }

    private fun publishLog(delta: String) {
        if (delta.isEmpty()) return
        appendRawStream(stripAnsi(delta))
    }

    /** App 自己输出的一整行，立即入列（不做半行缓冲）；[n/5] 标题行驱动阶段切换 */
    private fun appendLog(line: String) {
        val clean = stripAnsi(line).trimEnd()
        if (clean.isEmpty()) return
        val stage = stageOf(clean)
        if (stage > 0) currentStage = stage
        addLines(listOf(LogLine(currentStage, clean, summary = isSummary(clean))))
        if (stage > 0) {
            _state.value = _state.value.copy(currentStage = stage)
        }
    }

    /** exploit 原始输出流：拆行并合并半行，行属于当前阶段 */
    private fun appendRawStream(text: String) {
        if (text.isEmpty()) return
        val combined = pendingPartial + text
        val pieces = combined.split("\n")
        val complete: List<String>
        if (combined.endsWith("\n")) {
            complete = pieces.dropLast(1) // 末尾 "" 是拆分产物
            pendingPartial = ""
        } else {
            complete = pieces.dropLast(1)
            pendingPartial = pieces.last()
        }
        if (complete.isEmpty()) return
        val cleaned = complete.map { stripAnsi(it).trimEnd() }
        addLines(cleaned.map { LogLine(currentStage, it, summary = isSummary(it)) })
    }

    private fun addLines(lines: List<LogLine>) {
        if (lines.isEmpty()) return
        val newLines = (_state.value.logLines + lines).takeLast(MAX_LOG_LINES)
        _state.value = _state.value.copy(logLines = newLines)
    }

    /** 解析 "[n/5] 标题" 中的阶段号，非阶段行返回 0 */
    private fun stageOf(line: String): Int {
        val m = STAGE_PATTERN.find(line) ?: return 0
        return m.groupValues[1].toInt()
    }

    /** 是否总结性标题行（粗略模式只显示这些） */
    private fun isSummary(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("✔") || t.startsWith("✗") || t.startsWith("🎉") ||
            t.startsWith("⚠") || t.startsWith("◆")
        ) return true
        if (STAGE_PATTERN.containsMatchIn(t)) return true
        if (t.startsWith("[+]") || t.startsWith("[-]")) {
            return SUMMARY_RAW_ANY.any { t.contains(it) }
        }
        return SUMMARY_KEYWORDS.any { t.contains(it) }
    }

    /**
     * 导出完整日志到系统下载目录（MediaStore，无需权限）。
     * @return 结果提示文案
     */
    suspend fun dumpLog(): String = withContext(Dispatchers.IO) {
        runCatching {
            val content = buildString {
                _state.value.logLines.forEach { appendLine(it.text) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "rootmys9280-log.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = app.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建下载项")
                app.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    ?: error("无法写入下载项")
                "已导出到 下载/rootmys9280-log.txt (${content.length} 字符)"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val f = File(dir, "rootmys9280-log.txt")
                f.writeText(content)
                "已导出到 ${f.absolutePath}"
            }
        }.getOrElse { "导出失败: ${it.message}" }
    }

    private fun stripAnsi(s: String): String =
        s.replace(ANSI_ESCAPE, "").replace("\r", "")

    companion object {
        const val MAX_LOG_LINES = 4000
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;]*[a-zA-Z]")
        private val STAGE_PATTERN = Regex("^\\[([1-5])/5]")

        /** [+] / [-] 前缀行里视为总结性的内容 */
        private val SUMMARY_RAW_ANY = listOf(
            "attempt=", "preload supervisor", "slide-kaslr-ok",
            "umh result", "completed", "physrw-summary", "root=1", "root=0",
        )

        /** 其余行里的总结性关键词 */
        private val SUMMARY_KEYWORDS = listOf(
            "slide-kaslr-ok", "root umh result", "exploit completed",
            "retval=0 socket=1", "done=1 root=1", "uid=2000->0",
            "late-load", "exit=", "Permission denied", "失败", "成功", "错误",
        )
    }
}
