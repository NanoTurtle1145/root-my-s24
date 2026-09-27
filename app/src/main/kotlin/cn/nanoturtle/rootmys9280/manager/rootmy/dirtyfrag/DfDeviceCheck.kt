package cn.nanoturtle.rootmys9280.manager.rootmy.dirtyfrag

import java.io.File

/**
 * DirtyFrag 引擎的设备自检（alpha1）。
 *
 * 设计原则：**只读探测，不改动任何东西**。任一项不通过就应该拒绝动手，
 * 避免"patch 到一半失败"留下半残状态（页缓存/只读分区都被动过）。
 *
 * 全部走 Java 侧文件 API —— 下面这些路径对普通 app 都是可读的，
 * 所以自检本身**不需要 Shizuku、不需要 root**（这正是 DirtyFrag 相对
 * GhostLock 的核心优势：整条链都在 app 权限内完成）。
 *
 * 注：SELinux 标签（vendor_file / crash_dump_exec）需要 `ls -Z`，属于 shell 能力，
 * 本类不做；alpha2 再由引擎层用现有的 shell 通道补测。
 */
object DfDeviceCheck {

    /** 单条探测结果。 */
    data class Item(val name: String, val ok: Boolean, val detail: String)

    /** 整体报告。 */
    data class Report(
        val items: List<Item>,
        val device: String,
        val kernel: String,
    ) {
        val allOk: Boolean get() = items.isNotEmpty() && items.all { it.ok }

        /** 渲染成一段可直接进运行日志的文本。 */
        fun render(): String = buildString {
            append("DirtyFrag 自检: ").append(if (allOk) "全部通过 ✓" else "存在缺口 ✗")
            append(" (").append(device)
            if (kernel.isNotEmpty()) append(" / ").append(kernel)
            append(")\n")
            for (it in items) {
                append("  ").append(if (it.ok) "✓ " else "✗ ").append(it.name)
                append(" — ").append(it.detail).append('\n')
            }
        }
    }

    /** 必需路径 → 用途。顺序即报告顺序。 */
    private val TARGETS = listOf(
        "/vendor/lib64/libstagefrighthw.so" to "ko 落点(vendor_file 标签, 可被 modprobe)",
        "/apex/com.android.runtime/bin/crash_dump64" to "读页跳板(应用可无权限调用)",
        "/system/lib64/libc.so" to "libc 钩子(__libc_init)",
        "/system/lib64/libc++.so" to "libc++ 钩子(ostream sentry)",
        "/vendor/bin/modprobe" to "ko 加载入口(vendor_modprobe 域)",
        "/system/bin/logcat" to "DEFEX 绕过用的 bind-mount 落点",
    )

    /**
     * 采集自检报告。
     *
     * @param deviceModel 设备型号（调用方从 Build.MODEL 传）
     * @param kernel      内核 release（调用方从 System.getProperty("os.version") 传）
     */
    fun collect(deviceModel: String, kernel: String): Report {
        val items = TARGETS.map { (path, why) ->
            val f = File(path)
            val exists = runCatching { f.exists() }.getOrDefault(false)
            val detail = if (!exists) {
                "缺失 — $why"
            } else {
                // File.length()/canonicalPath 会跟随符号链接：正好用来识别
                // libc.so → APEX、modprobe → toolbox 这类跳板
                val len = runCatching { f.length() }.getOrDefault(0L)
                val real = runCatching { f.canonicalPath }.getOrNull()
                val body = if (real != null && real != path) "$len B → $real" else "$len B"
                "$body — $why"
            }
            Item(path, exists, detail)
        }
        return Report(items, deviceModel, kernel)
    }
}
