package cn.nanoturtle.rootmys9280.manager.rootmy

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.nanoturtle.rootmys9280.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * 根流程 ViewModel：
 * 1. 检查/申请 Shizuku 权限
 * 2. 把 assets 里的载荷推到 /data/local/tmp
 * 3. LD_PRELOAD 触发 CVE-2026-43499
 * 4. 等待 root 标记
 * 5. KernelSU late-load
 *
 * 目标：SM-S24 系列（S9210/S9260/S9280）+ 国行 Z Fold6
 *  - 国行：DZF2（覆盖 DZE2–DZG1，载荷 cve-2026-43499）/ BYH7（One UI 7，载荷 cve-2026-43499-byh7）
 *  - 国行 Z Fold6：与 S24 同 GKI 构建号，已验证共用 DZF2 载荷（cve-2026-43499）
 *  - 港版/台版：同构建号共用载荷——DZE2（范围 DZE2–DZG1，载荷 cve-2026-43499-dze2）/ CZA1（One UI 8.0）
 *  - DZG1 已禁用：DZG1 载荷真机早期无声崩溃，且与 DZF2 同构建号，直接走 DZF2 载荷
 */
class RootViewModel(app: Application) : AndroidViewModel(app) {
    private val app: Application = app

    /**
     * 崩溃恢复日志：exploit 可能在内核层面触发 panic/重启（OneUI7 测试曾出现
     * "获得 root 后手机马上自动重启"），内存日志随之丢失。每条日志实时追加到
     * 本文件（fsync 落盘），App 重启后从文件恢复，导出/排查不再丢日志。
     */
    private val persistFile: File by lazy { File(app.filesDir, "rootflow.log") }

    /** 机型/固件标识（如 SM-S9280 或 S9280ZCS6DZF2），供导出文件名使用；持久化以便崩溃后仍可用。 */
    private var deviceBuildTag: String
        get() = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_BUILD_TAG, null) ?: "unknown"
        set(value) {
            app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
                .edit().putString(KEY_DEVICE_BUILD_TAG, value).apply()
        }

    /** 目标系统版本（决定使用哪份 exploit 载荷）。按地区分组：国行 / 港版台版。 */
    enum class FirmwareVersion(
        val assetName: String,
        val label: String,
        val range: String,
        val region: Region,
        val enabled: Boolean = true,
    ) {
        // —— 国行 ——
        DZF2("cve-2026-43499", "One UI 8.5", "DZE2–DZG1", Region.CHINA),
        BYH7("cve-2026-43499-byh7", "One UI 7", "BYH7", Region.CHINA),
        // —— 国行 Z Fold6 —— 与 S24 同 GKI 构建号，已验证共用 DZF2 载荷成功
        ZFOLD6("cve-2026-43499", "Z Fold6", "国行 Z Fold6（共用 DZF2 载荷）", Region.CHINA),
        // —— 港版/台版 —— 港台同构建号可共用载荷，台版直接选用港版条目
        // （港版 DZE2 kmalloc_caches=0x176c6f8 与国行 0x176cbb8 不同，不能用 DZF2 载荷）
        CZA1("cve-2026-43499-cza1", "One UI 8.0", "CZA1", Region.HONGKONG_TAIWAN),
        DZE2("cve-2026-43499-dze2", "One UI 8.5", "DZE2–DZG1", Region.HONGKONG_TAIWAN),
    }

    /** 地区分组：国行 / 港版台版（同构建号共用载荷） */
    enum class Region(val label: String) {
        CHINA("国行"),
        HONGKONG_TAIWAN("港版/台版"),
    }

    /** 授权方式：Shizuku（需装 Shizuku App）或无线调试（Android 11+ 直连） */
    enum class AuthMethod { SHIZUKU, ADB_WIRELESS }

    /** 一条日志（stage=所属阶段 0=阶段外, summary=是否为总结性标题行） */
    data class LogLine(val stage: Int, val text: String, val summary: Boolean = false)

    data class UiState(
        /** 完整日志（每行一条，跨 stage 累积，不再被新输出覆盖） */
        val logLines: List<LogLine> = emptyList(),
        /** 当前执行到的阶段（1..5，0=未开始） */
        val currentStage: Int = 0,
        val busy: Boolean = false,
        val rooted: Boolean = false,
        /** KSU 驱动是否已加载（由 refreshKsuStatus 检测，重启后仍准确） */
        val ksuLoaded: Boolean = false,
        /** KNOX 状态展示文案（由 refreshKnox 填充） */
        val knoxState: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** 一次性事件：达到捐赠里程碑时发射（携带当前成功次数）。 */
    private val _donationEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val donationEvent: SharedFlow<Int> = _donationEvent

    private val donationManager = DonationManager(app)

    /** exploit 进程的原始输出累积缓冲（跨轮询保留，用于计算增量） */
    private val captured = StringBuilder()
    /** 未以换行结尾的半行（下次追加时续上） */
    private var pendingPartial = ""
    /** 当前阶段（由 [n/5] 标题行驱动） */
    private var currentStage = 0

    private val payloadName: String get() = firmwareVersion.assetName
    private val rootHelperName = "cve-2026-43499-root"
    private val ksudName = "ksud-selected"
    private val PREFS_SETTINGS = "settings"
    private val PREFS_FIRMWARE = "firmware_version"
    private val KEY_AUTH_METHOD = "auth_method"

    /** 实验性功能：无线调试授权（默认关闭；设置页开启后主页才显示相关控件） */
    private val KEY_ADB_WIRELESS_ENABLED = "adb_wireless_enabled"

    /** 当前 shell 执行器（Shizuku 或无线调试 adb），全部命令经由此执行。 */
    var shellExecutor: ShellExecutor = ShizukuController
        private set

    private val _adbWirelessEnabled = MutableStateFlow(false)
    /** 无线调试授权是否启用（设置页开关；主页订阅此状态决定是否显示无线调试控件） */
    val adbWirelessEnabled: StateFlow<Boolean> = _adbWirelessEnabled

    init {
        // 无线调试授权：注入 RSA 密钥存储目录（App 私有目录），恢复上次连接状态
        AdbWirelessController.init(File(app.filesDir, "adb"))
        // 恢复上次选择的授权方式（Shizuku 或无线调试）
        val prefs = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_AUTH_METHOD, AuthMethod.SHIZUKU.name)
        val adbEnabled = prefs.getBoolean(KEY_ADB_WIRELESS_ENABLED, false)
        _adbWirelessEnabled.value = adbEnabled
        if (adbEnabled && saved == AuthMethod.ADB_WIRELESS.name) {
            shellExecutor = AdbWirelessController
        } else if (!adbEnabled && saved == AuthMethod.ADB_WIRELESS.name) {
            // 无线调试被禁用但上次选了它：强制回退 Shizuku，避免流程引用未启用的后端
            prefs.edit().putString(KEY_AUTH_METHOD, AuthMethod.SHIZUKU.name).apply()
        }
        restorePersistedLog()
    }

    /** 设置页开关：启用/禁用无线调试授权。禁用时若当前是无线调试则回退 Shizuku 并断开。 */
    fun setAdbWirelessEnabled(enabled: Boolean) {
        val prefs = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ADB_WIRELESS_ENABLED, enabled).apply()
        _adbWirelessEnabled.value = enabled
        if (!enabled && authMethod == AuthMethod.ADB_WIRELESS) {
            setAuthMethod(AuthMethod.SHIZUKU)
            AdbWirelessController.disconnect()
        }
    }

    /** 当前授权方式（持久化；Compose 可观察） */
    val authMethod: AuthMethod
        get() = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_AUTH_METHOD, AuthMethod.SHIZUKU.name)
            .let { runCatching { AuthMethod.valueOf(it ?: AuthMethod.SHIZUKU.name) }.getOrDefault(AuthMethod.SHIZUKU) }

    /** 切换授权方式（立即生效，下次运行使用）。 */
    fun setAuthMethod(method: AuthMethod) {
        app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY_AUTH_METHOD, method.name).apply()
        shellExecutor = when (method) {
            AuthMethod.SHIZUKU -> ShizukuController
            AuthMethod.ADB_WIRELESS -> AdbWirelessController
        }
    }

    /** 调试选项：自动保存日志到磁盘 */
    private val KEY_AUTO_SAVE_LOG = "auto_save_log"

    /** 机型/固件标识（供导出文件名使用） */
    private val KEY_DEVICE_BUILD_TAG = "device_build_tag"

    /** 目标固件版本（持久化；切换后立即生效并触发 Compose 重组） */
    var firmwareVersion: FirmwareVersion
        get() = _firmwareVersion.value
        set(value) {
            if (_firmwareVersion.value == value) return
            _firmwareVersion.value = value
            app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
                .edit().putString(PREFS_FIRMWARE, value.name).apply()
        }

    /** Compose 可观察的固件版本状态（普通属性读写 SharedPreferences 不会触发重组） */
    private val _firmwareVersion: MutableStateFlow<FirmwareVersion> = MutableStateFlow(
        run {
            val name = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
                .getString(PREFS_FIRMWARE, FirmwareVersion.DZF2.name)
            FirmwareVersion.entries.firstOrNull { it.name == name } ?: FirmwareVersion.DZF2
        },
    )

    /** 供独立选择页收集的版本状态流（进程级单例 VM，跨 Activity 同步） */
    val firmwareVersionState: StateFlow<FirmwareVersion> = _firmwareVersion

    private val tmpPayload: String get() = "/data/local/tmp/$payloadName"
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
                appendLog("✗ " + app.getString(R.string.log_failed, t.message))
            } finally {
                // 唤醒屏幕（如果运行期间自动熄屏了）。shell 通道可能中途断开，
                // 这里必须兜底：finally 里的异常会覆盖上面的 catch，导致 app 崩溃。
                if (autoScreenOff) {
                    runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            shellExecutor.shell("input keyevent 26")
                        }
                    }
                    appendLog("◆ " + app.getString(R.string.log_woken))
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

    /** 调试选项：自动保存日志到磁盘（崩溃/重启后自动恢复）。默认开启。 */
    fun setAutoSaveLog(enabled: Boolean) {
        app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SAVE_LOG, enabled).apply()
    }

    val autoSaveLog: Boolean
        get() = app.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SAVE_LOG, true)

    fun clearLog() {
        captured.clear()
        pendingPartial = ""
        currentStage = 0
        _state.value = _state.value.copy(logLines = emptyList(), currentStage = 0)
        runCatching { persistFile.delete() }
    }

    /** 供 UI 追加提示行（如导出结果），不改变阶段 */
    fun notify(msg: String) {
        appendLog(msg)
    }

    /**
     * 自动申请 shell 权限（按当前授权方式）：
     * - Shizuku：已授权返回 true；Shizuku 在运行则弹系统授权框；未运行返回 false
     * - 无线调试：已连接返回 true；未连接返回 false（需先走 connectAdbWireless）
     * 幂等，可安全地在页面加载时调用。
     */
    suspend fun ensureShizukuPermission(): Boolean = when (authMethod) {
        AuthMethod.SHIZUKU -> {
            if (ShizukuController.isGranted()) true
            else if (ShizukuController.pingUntilRunning(timeoutMillis = 2_000)) ShizukuController.requestPermission()
            else false
        }
        AuthMethod.ADB_WIRELESS -> AdbWirelessController.isConnected()
    }

    /**
     * 无线调试直连：连接「开发者选项 → 无线调试」显示的 IP:端口（连接端口 39xxx）。
     * 首次连接设备会弹 RSA 指纹确认框，用户点「允许」后完成认证。
     * @return null=成功；非 null=失败原因（含需用户点允许等提示）
     */
    suspend fun connectAdbWireless(host: String, portText: String): String? {
        val port = portText.trim().toIntOrNull()
            ?: return app.getString(R.string.adb_wireless_bad_port)
        val hostTrimmed = host.trim()
        if (hostTrimmed.isEmpty()) return app.getString(R.string.adb_wireless_bad_host)
        val (ok, message) = withContext(Dispatchers.IO) {
            AdbWirelessController.connect(hostTrimmed, port)
        }
        if (!ok) {
            // 常见失败：连接拒绝（端口不对）、认证超时（忘了点允许）
            return message
        }
        appendLog("✔ " + app.getString(R.string.adb_wireless_connected, hostTrimmed, port))
        return null
    }

    /** 断开无线调试连接。 */
    fun disconnectAdbWireless() {
        AdbWirelessController.disconnect()
    }

    /**
     * 无线调试配对（adb pair）：输入系统「使用配对码配对设备」显示的
     * IP:配对端口（37xxx）+ 6 位配对码，预授权本机 RSA 公钥。
     * 配对成功后 connect 39xxx 连接端口免 RSA 指纹弹窗。
     * @return null=成功；非 null=失败原因
     */
    suspend fun pairAdbWireless(host: String, pairPortText: String, pairCode: String): String? {
        val port = pairPortText.trim().toIntOrNull()
            ?: return app.getString(R.string.adb_wireless_bad_port)
        val hostTrimmed = host.trim()
        if (hostTrimmed.isEmpty()) return app.getString(R.string.adb_wireless_bad_host)
        val code = pairCode.trim()
        if (code.isEmpty()) return app.getString(R.string.adb_wireless_bad_pair_code)
        return withContext(Dispatchers.IO) {
            AdbWirelessController.pair(hostTrimmed, port, code)
        }?.also { err ->
            if (err.contains("pairing code", ignoreCase = true)) {
                appendLog("✘ " + app.getString(R.string.log_adb_pair_code_wrong))
            } else {
                appendLog("✘ " + app.getString(R.string.log_adb_pair_failed, err))
            }
        }
    }

    /**
     * 通知配对：通过 mDNS 自动发现本机无线调试配对端口，
     * 弹出通知让用户输入 6 位配对码（RemoteInput），
     * 配对成功后自动连接。无需手动输入 IP/端口。
     * 由 AuthCard 的「通知配对」按钮调用。
     * @return null=已开始；非 null=失败原因（如通知权限未开启）
     */
    fun startAdbPairingNotification(context: android.content.Context): String? {
        if (AdbPairingFlow.isSearching()) return null
        if (AdbWirelessController.getAdbKey() == null) {
            return app.getString(R.string.log_adb_key_not_ready)
        }
        val err = AdbPairingFlow.startSearch(context)
        if (err == null) {
            appendLog("ℹ " + app.getString(R.string.log_adb_pair_notify_started))
        }
        return err
    }

    /**
     * 读取 KNOX 状态（经 Shizuku 读只读属性，不影响熔断判断）。
     * 注意：本流程不熔断 KNOX；warranty_bit=0 为完好。
     */
    suspend fun refreshKnox() {
        val bit = runCatching {
            shellExecutor.capture(
                arrayOf("/system/bin/sh", "-c", "getprop ro.boot.warranty_bit 2>&1")
            ).trim()
        }.getOrDefault("")
        val vbs = runCatching {
            shellExecutor.capture(
                arrayOf("/system/bin/sh", "-c", "getprop ro.boot.verifiedbootstate 2>&1")
            ).trim()
        }.getOrDefault("")
        val text = when {
            bit == "1" -> app.getString(R.string.log_knox_tripped)
            bit == "0" && vbs == "green" -> app.getString(R.string.log_knox_green)
            bit == "0" -> app.getString(R.string.log_knox_ok, vbs.ifBlank { "?" })
            vbs.isNotBlank() -> app.getString(R.string.log_knox_unknown, vbs)
            else -> app.getString(R.string.log_knox_needs)
        }
        _state.value = _state.value.copy(knoxState = text)
    }

    /**
     * 检测 KernelSU 驱动是否已加载（无需 root）：
     * /proc/modules 列出已加载模块，或 /sys/module/kernelsu 存在即驱动在内核中。
     * 重启后依然准确，不依赖本次会话是否跑过 root 流程。
     *
     * 注意：必须经 Shizuku（shell 权限）检测——app 进程直接读 /proc/modules 会被
     * SELinux / KSU 隐藏机制拒绝（Permission denied），而 shell 视角能看到 kernelsu。
     */
    suspend fun refreshKsuStatus() = withContext(Dispatchers.IO) {
        val loaded = runCatching {
            if (shellExecutor.isReady()) {
                // shell 权限经执行器检测
                shellExecutor.shell(
                    "grep -q 'kernelsu' /proc/modules 2>/dev/null || " +
                        "ls /sys/module/kernelsu >/dev/null 2>&1"
                ).first == 0
            } else {
                // 执行器不可用时的兜底（部分环境 app 可直接读）
                val p = ProcessBuilder(
                    "/system/bin/sh", "-c",
                    "grep -q 'kernelsu' /proc/modules 2>/dev/null || " +
                        "ls /sys/module/kernelsu >/dev/null 2>&1"
                ).start()
                p.waitFor() == 0
            }
        }.getOrDefault(false)
        _state.value = _state.value.copy(ksuLoaded = loaded, rooted = loaded)
    }

    private suspend fun runRootFlow() = withContext(Dispatchers.IO) {
        appendLog("◆ " + app.getString(R.string.log_starting))
        appendLog("◆ " + app.getString(R.string.log_payload, payloadName))

        // 1. 授权检查（按当前授权方式：Shizuku 或无线调试）
        appendLog(app.getString(R.string.log_check_shizuku))
        when (authMethod) {
            AuthMethod.SHIZUKU -> {
                if (!ShizukuController.pingUntilRunning()) {
                    throw IllegalStateException(app.getString(R.string.log_shizuku_not_running))
                }
                if (!ShizukuController.requestPermission()) {
                    throw IllegalStateException(app.getString(R.string.log_shizuku_denied))
                }
                appendLog("✔ " + app.getString(R.string.log_shizuku_ready))
            }
            AuthMethod.ADB_WIRELESS -> {
                if (!AdbWirelessController.isConnected()) {
                    throw IllegalStateException(app.getString(R.string.log_adb_not_connected))
                }
                appendLog("✔ " + app.getString(R.string.log_adb_ready))
            }
        }

        // 1.5 机型报告：完整设备/固件/内核信息，写入日志头部（便于崩溃后从日志识别设备）
        collectDeviceInfo()

        // 2. 推送载荷
        appendLog(app.getString(R.string.log_push_payload))
        extractAsset(payloadName)
        extractAsset(rootHelperName)
        extractAsset(ksudName)
        val stagedPayload = copyToTmp(payloadName, tmpPayload, "755")
        val stagedHelper = copyToTmp(rootHelperName, tmpRootHelper, "755")
        copyToTmp(ksudName, tmpKsud, "755")
        appendLog("✔ " + app.getString(R.string.log_push_done, payloadName, stagedPayload.length(), rootHelperName, stagedHelper.length()))

        // 2.5 诊断：管道限制（F_SETPIPE_SZ EPERM 的根因排查）
        val pipeMax = shellExecutor.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/sys/fs/pipe-max-size 2>&1")
        ).trim()
        val pipeUser = shellExecutor.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/sys/fs/pipe-user-pages-soft 2>&1 || cat /proc/sys/fs/pipe-user-pages-hard 2>&1 || echo n/a")
        ).trim()
        val uname = shellExecutor.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/version 2>&1 | head -c 200")
        ).trim()
        appendLog("◆ " + app.getString(R.string.log_diag, pipeMax.ifBlank { "?" }, pipeUser.ifBlank { "?" }))
        appendLog("◆ " + app.getString(R.string.log_diag_version, uname.ifBlank { "?" }))
        if (pipeMax.isNotBlank()) {
            val maxKb = pipeMax.toLongOrNull() ?: 0
            if (maxKb > 0 && maxKb < 131072) {
                appendLog("⚠ " + app.getString(R.string.log_pipe_warn, maxKb))
            }
        }

        // 2.6 清理残留：上次失败的 exploit 子进程/守护进程会残留并污染 uid 2000 的
        //     pipe_bufs 配额，导致 F_SETPIPE_SZ EPERM（16/16 失败根因之一）
        appendLog(app.getString(R.string.log_cleanup))
        val cleanup = shellExecutor.shell(
            "pkill -9 -f 'cve-2026-43499' 2>/dev/null; " +
                "pkill -9 -f 'cve43499' 2>/dev/null; " +
                "rm -f /data/local/tmp/temp_su.sock /data/local/tmp/ksud-s25u-kdp /data/local/tmp/.ksud-stage; echo ok"
        )
        appendLog("✔ " + app.getString(R.string.log_cleanup_done, cleanup.first))

        // 3. 触发 exploit
        appendLog(app.getString(R.string.log_trigger))
        // 运行期间自动熄屏：显示驱动停止 → 消除最大崩溃源（worklist 竞态）
        if (autoScreenOff) {
            shellExecutor.shell("input keyevent 26")
            appendLog("◆ " + app.getString(R.string.log_screen_off))
        }
        val env = arrayOf(
            "EXPLOIT_ATTEMPTS=30",
            "P0_ATTEMPT_TIMEOUT_SEC=45",
            "EXPLOIT_ATTEMPT_TIMEOUT_SEC=120",
            "CVE43499_ROOT_HELPER=$tmpRootHelper",
            "LD_PRELOAD=$tmpPayload",
        )
        val process = shellExecutor.exec(
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
                throw IllegalStateException(app.getString(R.string.log_exploit_timeout))
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
            throw IllegalStateException(app.getString(R.string.log_exploit_incomplete))
        }
        if (!rootObtained) {
            throw IllegalStateException(app.getString(R.string.log_no_root))
        }
        appendLog("✔ " + app.getString(R.string.log_temp_root))

        // 4. KernelSU late-load（经 root 守护进程 temp_su.sock 以 root 执行）
        appendLog(app.getString(R.string.log_lateload))
        appendLog("◆ " + app.getString(R.string.log_screen_off_hint))
        // ksud 必须放在守护进程硬编码的加载路径 (su_daemon 的 KSU_LOADER_PATH)
        val stageCmd = "cp $tmpKsud /data/local/tmp/ksud-s25u-kdp && " +
            "cp $tmpKsud /data/local/tmp/.ksud-stage && " +
            "chmod 755 /data/local/tmp/ksud-s25u-kdp /data/local/tmp/.ksud-stage"
        val stageResult = shellExecutor.shell(stageCmd)
        if (stageResult.first != 0) {
            throw IllegalStateException(app.getString(R.string.log_stage_fail, stageResult.first, stageResult.second.trim().takeLast(200)))
        }
        appendLog("✔ " + app.getString(R.string.log_ksud_staged))
        // 以 su 客户端模式连接 root 守护进程，守护进程 fork root 子进程执行:
        //   ksud late-load --ephemeral --package-name me.weishu.kernelsu
        val ksu = shellExecutor.shell("$tmpRootHelper --late-load 2>&1")
        appendLog("ksud late-load: exit=${ksu.first}\n${ksu.second.trim().takeLast(300)}")
        if (ksu.first != 0) {
            throw IllegalStateException(app.getString(R.string.log_lateload_fail, ksu.first))
        }
        appendLog("✔ " + app.getString(R.string.log_driver_loaded))

        // 5. 验证（late-load 内部已做 KSU 驱动 ioctl 校验；root 由 KernelSU 管理器提供）
        appendLog(app.getString(R.string.log_verify))
        val daemonCheck = shellExecutor.capture(
            arrayOf("/system/bin/sh", "-c", "ls -la /data/local/tmp/temp_su.sock 2>&1")
        )
        appendLog(app.getString(R.string.log_daemon_check, daemonCheck.trim()))
        appendLog("✔ " + app.getString(R.string.log_install_manager))

        _state.value = _state.value.copy(rooted = true)
        appendLog("🎉 " + app.getString(R.string.log_flow_done))

        // 捐赠里程碑：root 成功累计到 10/25/50/75/100… 时提示一次
        if (donationManager.recordSuccess()) {
            _donationEvent.tryEmit(donationManager.successCount)
        }
    }

    private fun extractAsset(name: String): File {
        val out = File(app.filesDir, name)
        app.assets.open(name).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    /**
     * 收集设备/固件/内核完整信息，写入日志头部（机型报告）。
     * 崩溃/重启后日志仍可据此识别设备，便于按机型归档与排查。
     */
    private suspend fun collectDeviceInfo() {
        val props = listOf(
            "ro.product.model",       // SM-S9280
            "ro.product.board",       // e3q
            "ro.build.version.release", // 15
            "ro.build.version.sdk",   // 35
            "ro.build.version.security_patch", // 2026-06-01
            "ro.build.version.incremental", // S9280ZCS6DZF2
            "ro.build.fingerprint",   // 完整指纹
            "ro.bootloader",          // S9280ZCS6DZF2
            "ro.boot.warranty_bit",   // KNOX 熔断状态
            "ro.boot.verifiedbootstate", // green
        )
        val values = mutableMapOf<String, String>()
        props.forEach { name ->
            values[name] = shellExecutor.capture(
                arrayOf("/system/bin/sh", "-c", "getprop $name 2>&1")
            ).trim()
        }
        val model = values["ro.product.model"].orEmpty()
        val board = values["ro.product.board"].orEmpty()
        val buildInc = values["ro.build.version.incremental"].orEmpty()
        val release = values["ro.build.version.release"].orEmpty()
        val patch = values["ro.build.version.security_patch"].orEmpty()
        val fingerprint = values["ro.build.fingerprint"].orEmpty()
        val bootloader = values["ro.bootloader"].orEmpty()
        val warranty = values["ro.boot.warranty_bit"].orEmpty()
        val vbs = values["ro.boot.verifiedbootstate"].orEmpty()

        appendLog("◆ 设备: ${model.ifBlank { "?" }} (${board.ifBlank { "?" }})")
        val sysLine = "Android " + release.ifBlank { "?" } +
            if (patch.isBlank()) "" else " / 安全补丁 $patch"
        appendLog("◆ 系统: $sysLine")
        val fwLine = buildInc.ifBlank { "?" } +
            if (bootloader.isBlank()) "" else " (bootloader $bootloader)"
        appendLog("◆ 固件: $fwLine")
        if (fingerprint.isNotBlank()) appendLog("◆ 指纹: $fingerprint")
        val knox = when {
            warranty == "1" -> "已熔断 (warranty_bit=1)"
            warranty == "0" && vbs == "green" -> "完好 (warranty_bit=0, verifiedbootstate=green)"
            else -> "warranty_bit=${warranty.ifBlank { "?" }}, verifiedbootstate=${vbs.ifBlank { "?" }}"
        }
        appendLog("◆ KNOX: $knox")

        // 记录机型标识，供导出文件名使用（如 rootmys9280-S9280ZCS6DZF2.txt）
        val buildTag = listOf(model, buildInc).firstOrNull { it.isNotBlank() && !it.contains("?") }
        val normalized = buildTag
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(40)
            ?: "unknown"
        deviceBuildTag = normalized
    }

    private fun copyToTmp(sourceName: String, target: String, mode: String): File {
        val src = File(app.filesDir, sourceName)
        shellExecutor.writeFile(target, mode, src.inputStream())
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
        persistLines(lines)
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

    /** 最近一次持久化写入失败原因（null=正常；供恢复/导出时诊断展示） */
    @Volatile
    private var persistFailure: String? = null

    /**
     * 追加日志到持久化文件（App 私有目录，逐条追加 + fsync 落盘）。
     * 崩溃/重启后由 [restorePersistedLog] 恢复，确保 exploit 触发内核 panic
     * 时日志不丢（OneUI7 测试曾出现 root 成功后立即重启导致日志丢失）。
     * 文件超限时保留尾部 [MAX_LOG_LINES] 行（与内存上限一致）。
     * 写入失败不再静默吞掉：记录 [persistFailure]，恢复/导出时展示诊断。
     */
    private fun persistLines(lines: List<LogLine>) {
        if (lines.isEmpty()) return
        if (!autoSaveLog) return // 调试选项关闭时不落盘
        try {
            persistFile.parentFile?.mkdirs()
            FileOutputStream(persistFile, true).use { fos ->
                val writer = OutputStreamWriter(fos, Charsets.UTF_8)
                lines.forEach { line -> writer.write(line.text); writer.write('\n'.code) }
                writer.flush()
                fos.fd.sync() // 落盘，防断电/panic 丢数据
            }
            persistFailure = null
        } catch (t: Throwable) {
            persistFailure = t.message ?: t.javaClass.simpleName
        }
        runCatching { trimPersistFile() }
    }

    /** 持久化文件只保留尾部 [MAX_LOG_LINES] 行（避免无限增长）。 */
    private fun trimPersistFile() {
        if (!persistFile.exists() || persistFile.length() < MAX_PERSIST_BYTES) return
        val all = persistFile.readLines()
        if (all.size <= MAX_LOG_LINES) return
        persistFile.writeText(all.takeLast(MAX_LOG_LINES).joinToString("\n") + "\n")
    }

    /** App 重启后从持久化文件恢复上次运行的日志（stage 按 [n/5] 重新推导）。 */
    private fun restorePersistedLog() {
        runCatching {
            if (!persistFile.exists()) return
            val lines = persistFile.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return
            var stage = 0
            val restored = lines.map { text ->
                val s = stageOf(text)
                if (s > 0) stage = s
                LogLine(stage, text, summary = isSummary(text))
            }
            // 恢复可见提示：让测试者明确看到"上次日志已自动恢复"（含行数），
            // 避免误以为 App 重启后日志丢失而只能手动导出。
            val notes = buildList {
                val failure = persistFailure
                if (failure != null) {
                    add(LogLine(0, "⚠ " + app.getString(R.string.log_persist_failed, failure), summary = true))
                }
                add(LogLine(0, "↻ " + app.getString(R.string.log_restored, lines.size), summary = true))
            }
            _state.value = _state.value.copy(
                logLines = (notes + restored).takeLast(MAX_LOG_LINES),
                currentStage = stage,
            )
        }
    }

    /**
     * 导出完整日志到系统下载目录（MediaStore，无需权限）。
     * 文件名按机型/固件命名（如 rootmys9280-S9280ZCS6DZF2.txt），
     * 便于多设备混测时按机型归档。
     * @return 结果提示文案
     */
    suspend fun dumpLog(): String = withContext(Dispatchers.IO) {
        runCatching {
            // 优先导出内存日志；若内存为空（重启后恢复失败等），兜底从持久化文件读取，
            // 确保"疯狂按保存"也能拿到完整内容。
            val content = buildString {
                val memLines = _state.value.logLines
                if (memLines.isNotEmpty()) {
                    memLines.forEach { appendLine(it.text) }
                } else if (persistFile.exists()) {
                    persistFile.readLines().forEach { appendLine(it) }
                } else {
                    val failure = persistFailure
                    if (failure != null) {
                        appendLine("⚠ " + app.getString(R.string.log_persist_failed, failure))
                    }
                }
            }
            val displayName = "rootmys9280-$deviceBuildTag.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = app.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error(app.getString(R.string.log_export_entry_fail))
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray())
                    out.flush()
                    // MediaStore 流底层是 ParcelFileDescriptor，尽量 fsync，
                    // 避免导出后立刻发生内核 panic/重启时留下空/零填充文件。
                    runCatching {
                        (out as? java.io.FileOutputStream)?.fd?.sync()
                    }
                } ?: error(app.getString(R.string.log_export_write_fail))
                app.getString(R.string.log_export_ok_name, displayName, content.length)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val f = File(dir, displayName)
                f.writeText(content)
                app.getString(R.string.log_export_ok_path, f.absolutePath)
            }
        }.getOrElse { app.getString(R.string.log_export_fail, it.message) }
    }

    private fun stripAnsi(s: String): String =
        s.replace(ANSI_ESCAPE, "").replace("\r", "")

    companion object {
        const val MAX_LOG_LINES = 4000

        /** 持久化文件大小阈值（超过后裁剪到尾部 MAX_LOG_LINES 行） */
        private const val MAX_PERSIST_BYTES = 1L shl 20 // 1 MiB

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
