package cn.nanoturtle.rootmys9280.manager.rootmy

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行日志的**本机存档**。
 *
 * 之前日志只存在 ViewModel 的内存缓冲里：App 一杀、一重启就没了，用户反馈时也拿不到任何
 * 可引用的东西（服务器那边的链接只有我们自己看得到）。现在每轮运行结束都把本轮日志落盘，
 * 并给每条日志分配一个**短编号**（取自 RUN-ID 前 8 位）：
 *
 * - 用户在「运行日志」页能看到编号并一键复制，反馈时只贴编号即可
 * - 我们自己也能按编号在服务端日志里精确定位（服务端同样记录 RUN-ID）
 * - 保留最近 [MAX_FILES] 条，自动清理最旧的
 */
object RunLogStore {

    private const val DIR = "runlogs"
    private const val MAX_FILES = 30

    data class Entry(
        val code: String,
        val savedAt: Long,
        val outcome: String,
        val file: File,
    ) {
        fun timeLabel(): String =
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(savedAt))
    }

    /** RUN-ID 是 UUID；编号取前 8 个十六进制字符，够短也够区分。 */
    fun codeOf(runId: String): String =
        runId.replace("-", "").take(8).lowercase().ifEmpty { "unknown" }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * 保存一轮日志。文件内容自带头部（编号 / 完整 RUN-ID / 时间 / 结果 / 版本），
     * 就算用户只把这个 .log 文件发出来也能看懂。
     */
    fun save(context: Context, runId: String, logText: String, outcome: String): Entry? =
        runCatching {
            val code = codeOf(runId)
            val now = System.currentTimeMillis()
            val file = File(dir(context), "${now}_$code.log")
            val header = buildString {
                appendLine("# RootMyS24 运行日志")
                appendLine("# 日志编号: $code")
                appendLine("# RUN-ID: $runId")
                appendLine("# 保存时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))}")
                appendLine("# 结果: $outcome")
                appendLine("# " + "-".repeat(60))
            }
            file.writeText(header + logText)
            prune(context)
            Entry(code, now, outcome, file)
        }.getOrNull()

    fun list(context: Context): List<Entry> =
        runCatching {
            dir(context)
                .listFiles { f -> f.isFile && f.name.endsWith(".log") }
                .orEmpty()
                .sortedByDescending { it.lastModified() }
                .mapNotNull { f ->
                    val outcome = f.useLines { lines ->
                        lines.firstOrNull { it.startsWith("# 结果: ") }?.removePrefix("# 结果: ")
                    } ?: "?"
                    Entry(f.name.substringAfterLast('_').removeSuffix(".log"), f.lastModified(), outcome, f)
                }
        }.getOrDefault(emptyList())

    fun read(context: Context, code: String): String? =
        runCatching {
            dir(context).listFiles { f -> f.isFile && f.name.endsWith("_$code.log") }
                ?.maxByOrNull { it.lastModified() }
                ?.readText()
        }.getOrNull()

    private fun prune(context: Context) {
        val files = dir(context).listFiles { f -> f.isFile && f.name.endsWith(".log") }.orEmpty()
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach { runCatching { it.delete() } }
    }
}
