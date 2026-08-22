package cn.nanoturtle.rootmys9280.manager.rootmy

import java.io.InputStream

/**
 * Shell 权限执行器抽象：以 uid 2000 (shell) 权限执行命令。
 *
 * 两个实现：
 * - [ShizukuController]：经 Shizuku binder（需装 Shizuku App + adb 授权启动）
 * - [AdbWirelessController]：经无线调试 adb 通道（Android 11+ 无线调试直连授权）
 *
 * Root 流程只依赖此接口，不感知具体授权来源。
 */
interface ShellExecutor {
    /** 是否已就绪（binder 在 / adb 通道已建立）。 */
    fun isReady(): Boolean

    /** 以 shell 权限启动命令，返回进程（流式输出）。 */
    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process

    /** 执行单条命令，返回合并输出（失败时返回空串）。 */
    fun capture(cmd: Array<String>): String

    /** 执行 shell 脚本，返回 (退出码, 输出)。 */
    fun shell(cmd: String): Pair<Int, String>

    /** 把流写入远程路径（先删旧文件再 cat，最后 chmod）。 */
    fun writeFile(remotePath: String, mode: String, source: InputStream)
}
