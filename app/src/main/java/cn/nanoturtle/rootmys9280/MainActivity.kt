package cn.nanoturtle.rootmys9280

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.nanoturtle.rootmys9280.ui.theme.PREFS_NAME
import cn.nanoturtle.rootmys9280.ui.theme.RootMyS9280Theme
import cn.nanoturtle.rootmys9280.ui.theme.initAppTheme
import cn.nanoturtle.rootmys9280.ui.theme.setDynamicColor
import cn.nanoturtle.rootmys9280.ui.theme.setThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAppTheme(this)
        enableEdgeToEdge()
        setContent {
            RootMyS9280Theme {
                RootScreen()
            }
        }
    }
}

enum class RootTab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    Log("日志", Icons.AutoMirrored.Filled.List),
    About("关于", Icons.Filled.Info),
}

private const val PREFS_BRIEF_LOG = "brief_log"
private const val PREFS_AUTO_JUMP = "auto_jump_log"
private const val REPO_URL = "https://github.com/nanoturtle1145/root-my-s9280"

@Composable
fun RootScreen(vm: RootViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(RootTab.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            MiuixNavigationBar {
                RootTab.entries.forEach { item ->
                    MiuixNavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    ) { innerPadding ->
        when (tab) {
            RootTab.Home -> HomeScreen(
                state = state,
                onStart = {
                    vm.start()
                    val autoJump = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREFS_AUTO_JUMP, true)
                    if (autoJump) tab = RootTab.Log // 开始后自动跳到日志页
                },
                modifier = Modifier.padding(innerPadding),
            )
            RootTab.Log -> LogScreen(state, vm, Modifier.padding(innerPadding))
            RootTab.About -> AboutScreen(Modifier.padding(innerPadding))
        }
    }
}

// ---------------------------------------------------------------- 首页

@Composable
private fun HomeScreen(
    state: RootViewModel.UiState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var knox by remember { mutableStateOf("…") }
    var ksuLoaded by remember { mutableStateOf(false) }
    var ksuChecking by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        knox = readProp("ro.boot.warranty_bit")?.let {
            if (it == "1") "已熔断" else "完好"
        } ?: "未知"
        ksuChecking = true
        ksuLoaded = detectKernelSu()
        ksuChecking = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val rootedNow = ksuLoaded || state.rooted

        if (rootedNow) {
            // ---- 已 Root：KSU 风格对钩大卡片（不显示开始按钮）----
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("工作中 <LKM> [越狱模式]", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "KernelSU 驱动已加载 · v3.2.5 (32525)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "重启手机后需重新运行一次 Root",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { scope.launch { refresh() } },
                        enabled = !ksuChecking,
                    ) {
                        Text(if (ksuChecking) "检测中..." else "重新检测")
                    }
                }
            }
        } else {
            // ---- 未 Root：状态大卡 + 开始按钮（KSU Manager 风格）----
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(13.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("RootMyS9280", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "SM-S9280 国行 · 免解锁 root",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (statusText, statusColor) = when {
                            state.busy -> "运行中" to MaterialTheme.colorScheme.primary
                            ksuChecking -> "检测中" to MaterialTheme.colorScheme.onSurfaceVariant
                            else -> "就绪" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = statusColor.copy(alpha = 0.12f),
                        ) {
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onStart,
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text(if (state.busy) "运行中..." else "开始 Root", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- 指标 2×2 ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("KernelSU", if (rootedNow) "已加载" else "未加载", Modifier.weight(1f))
            MetricCard("KNOX", knox, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("载荷", "修正版", Modifier.weight(1f))
            MetricCard("阶段", stageName(state.currentStage), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        // ---- 设备信息（从关于页移入）----
        Text("设备信息", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                InfoRow("型号", "${Build.MODEL} (${Build.DEVICE})")
                Spacer(Modifier.height(6.dp))
                InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Spacer(Modifier.height(6.dp))
                InfoRow("指纹", Build.FINGERPRINT.take(56))
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 当前阶段日志预览 ----
        Text("当前阶段日志", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        val stageLines = state.logLines.filter { it.stage == state.currentStage }.takeLast(8)
        if (stageLines.isEmpty()) {
            Text(
                "（暂无日志，点“开始 Root”后这里会显示当前阶段进度）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            stageLines.forEach { line ->
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = logLineColor(line.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 检测 KernelSU 驱动是否已加载（无需 root）：/proc/modules 或 /sys/module */
private suspend fun detectKernelSu(): Boolean = withContext(Dispatchers.IO) {
    try {
        val p = ProcessBuilder(
            "/system/bin/sh", "-c",
            "grep -q 'kernelsu' /proc/modules 2>/dev/null || ls /sys/module/kernelsu >/dev/null 2>&1",
        ).redirectErrorStream(true).start()
        p.waitFor() == 0
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.28f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.72f),
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    MiuixCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

private fun stageName(stage: Int): String = when (stage) {
    1 -> "Shizuku"
    2 -> "推送载荷"
    3 -> "exploit"
    4 -> "late-load"
    5 -> "验证"
    else -> "未开始"
}

private fun themeModeName(mode: Int): String = when (mode) {
    1 -> "浅色"
    2 -> "深色"
    else -> "跟随系统"
}

/** 主题模式选择对话框（KSU 风格单选） */
@Composable
private fun ThemeModeDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题模式") },
        text = {
            Column {
                listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == mode,
                            onClick = { onSelect(mode) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---------------------------------------------------------------- 日志页

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogScreen(
    state: RootViewModel.UiState,
    vm: RootViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var brief by remember { mutableStateOf(prefs.getBoolean(PREFS_BRIEF_LOG, false)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("运行日志", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                val segColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedButton(
                    selected = !brief,
                    onClick = { brief = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = segColors,
                ) { Text("详细") }
                SegmentedButton(
                    selected = brief,
                    onClick = { brief = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = segColors,
                ) { Text("粗略") }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val result = vm.dumpLog()
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                },
            ) { Text("导出") }
            OutlinedButton(onClick = { vm.clearLog() }) { Text("清空") }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()

        // 粗略模式只显示被标记的总结性标题行；详细模式显示全部历史
        val shown = if (brief) {
            state.logLines.filter { it.summary }
        } else {
            state.logLines
        }

        // 新日志出现或切换模式时自动滚到底部
        LaunchedEffect(shown.size, brief) {
            if (shown.isNotEmpty()) {
                listState.animateScrollToItem(shown.lastIndex)
            }
        }

        if (shown.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (brief) "（暂无总结性日志）" else "（日志将显示在这里）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown) { line ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (line.summary) FontWeight.Bold else FontWeight.Normal,
                        color = logLineColor(line.text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 关于页

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var briefLog by remember { mutableStateOf(prefs.getBoolean(PREFS_BRIEF_LOG, false)) }
    var autoJump by remember { mutableStateOf(prefs.getBoolean(PREFS_AUTO_JUMP, true)) }
    var knoxState by remember { mutableStateOf("检测中…") }
    var licenseDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        knoxState = readProp("ro.boot.warranty_bit")?.let {
            if (it == "1") "已熔断 (0x1)" else "完好 (0x0)"
        } ?: "未知"
    }

    licenseDialog?.let { (title, text) ->
        LicenseDialog(title = title, text = text, onDismiss = { licenseDialog = null })
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            current = cn.nanoturtle.rootmys9280.ui.theme.AppThemeState.themeMode,
            onSelect = { mode ->
                setThemeMode(context, mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- 头部 ----
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("RootMyS9280", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            "免解锁 root · SM-S9280 国行 DZF2\n基于 CVE-2026-43499 安全研究",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openUrl(context, REPO_URL) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "github.com/nanoturtle1145/root-my-s9280",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 通用（KSU 设置分组）----
        SectionTitle("通用")
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            // 主题模式（KSU 风格）
            ClickableRow(
                icon = Icons.Filled.Settings,
                title = "主题模式",
                subtitle = themeModeName(cn.nanoturtle.rootmys9280.ui.theme.AppThemeState.themeMode),
                onClick = { showThemeDialog = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            // 动态取色（Material You）
            SettingSwitchRow(
                icon = Icons.Filled.CheckCircle,
                title = "动态取色",
                subtitle = "Material You 动态配色",
                checked = cn.nanoturtle.rootmys9280.ui.theme.AppThemeState.dynamicColor,
                onCheckedChange = { setDynamicColor(context, it) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingSwitchRow(
                icon = Icons.Filled.Settings,
                title = "日志默认模式",
                subtitle = "打开日志页时默认使用粗略模式",
                checked = briefLog,
                onCheckedChange = {
                    briefLog = it
                    prefs.edit().putBoolean(PREFS_BRIEF_LOG, it).apply()
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingSwitchRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "开始后自动跳转日志页",
                subtitle = "点击“开始 Root”后自动切到日志页",
                checked = autoJump,
                onCheckedChange = {
                    autoJump = it
                    prefs.edit().putBoolean(PREFS_AUTO_JUMP, it).apply()
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- 关于 ----
        SectionTitle("关于")
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            ClickableRow(
                icon = Icons.Filled.Info,
                title = "检查更新",
                subtitle = "查看 GitHub Releases",
                onClick = { openUrl(context, "$REPO_URL/releases") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.Home,
                title = "项目主页",
                subtitle = REPO_URL.removePrefix("https://"),
                onClick = { openUrl(context, REPO_URL) },
            )
        }
        Spacer(Modifier.height(8.dp))
        InfoCard(
            rows = listOf(
                "App 版本" to "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "载荷版本" to "cve-2026-43499 修正版 (kmalloc_caches 0x176cbb8, worklist 竞态修复)",
                "目标机型" to "SM-S9280 (国行 CHC) · S9280ZCS6DZF2",
                "内核" to "6.1.145-android14-11-3254743",
                "KernelSU" to "v3.2.5 (32525) · 越狱模式",
                "KNOX 状态" to "$knoxState（bootloader 报告）",
            ),
        )
        Spacer(Modifier.height(16.dp))

        // ---- 源代码与许可 ----
        SectionTitle("源代码与许可")
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            ClickableRow(
                icon = Icons.Filled.CheckCircle,
                title = "RootMyS9280（本项目）",
                subtitle = "github.com/nanoturtle1145/root-my-s9280 · GPL-3.0",
                onClick = { licenseDialog = "GNU GPL v3（本项目）" to loadLicense(context, "gpl-3.0.txt") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.CheckCircle,
                title = "KernelSU",
                subtitle = "github.com/tiann/KernelSU · GPL-2.0",
                onClick = { licenseDialog = "GNU GPL v2（KernelSU）" to loadLicense(context, "gpl-2.0.txt") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.CheckCircle,
                title = "LSPosed",
                subtitle = "github.com/LSPosed/LSPosed · GPL-3.0",
                onClick = { licenseDialog = "GNU GPL v3（LSPosed）" to loadLicense(context, "gpl-3.0.txt") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.Info,
                title = "Zygisk-Next",
                subtitle = "github.com/Dr-TSNG/ZygiskNext",
                onClick = { openUrl(context, "https://github.com/Dr-TSNG/ZygiskNext") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.Info,
                title = "KnoxPatch",
                subtitle = "github.com/salvogiangri/KnoxPatch",
                onClick = { openUrl(context, "https://github.com/salvogiangri/KnoxPatch") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.Info,
                title = "Root-My-Galaxy",
                subtitle = "github.com/BuSung-dev/Root-My-Galaxy",
                onClick = { openUrl(context, "https://github.com/BuSung-dev/Root-My-Galaxy") },
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- 许可原文 ----
        SectionTitle("许可原文")
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            ClickableRow(
                icon = Icons.Filled.Warning,
                title = "GNU GPL v3.0 全文",
                subtitle = "本项目许可证（LICENSE）",
                onClick = { licenseDialog = "GNU GPL v3.0" to loadLicense(context, "gpl-3.0.txt") },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ClickableRow(
                icon = Icons.Filled.Warning,
                title = "GNU GPL v2.0 全文",
                subtitle = "KernelSU 等组件许可证",
                onClick = { licenseDialog = "GNU GPL v2.0" to loadLicense(context, "gpl-2.0.txt") },
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- FAQ ----
        SectionTitle("常见问题")
        FaqCard(
            listOf(
                "Q: 支持哪些机型/固件？" to
                    "A: 当前验证：SM-S9280 (CHC/TGY/BRI) S9280ZCS6DZF2。同一 e3q 平台（S24 系列）" +
                    "同代固件的符号基本一致，理论可适配，需真机验证；其他机型/固件需按仓库" +
                    "适配文档重新验证内核常量。",
                "Q: 内核版本必须完全一致才能成功吗？" to
                    "A: 不是。成功取决于 exploit 依赖的符号与结构布局是否一致，而非版本字符串。" +
                    "同构建（版本串一致）最稳；同平台不同构建号（如 DZE2/DZF2）GKI KMI 冻结保证" +
                    "大部分稳定，但个别非导出符号（如 kmalloc_caches）可能漂移，需逐符号对比修正；" +
                    "跨大版本（不同 KMI）需重新适配，且漏洞可能已被修复。",
                "Q: 为什么会卡死/重启？是正常现象吗？" to
                    "A: 正常。exploit 通过破坏内核内存提权，命中关键结构就会 oops 重启。" +
                    "官方参考实现同样需要多次尝试（S928U1 验证时第 2 轮 attempt 4/24 才成功）。" +
                    "失败/重启后重新运行即可。",
                "Q: 每次重启后都要重新 Root 吗？" to
                    "A: 是。bootloader 是锁定的，无法持久化写入内核模块。" +
                    "每次重启后运行一次“开始 Root”，再强制停止并重开 KernelSU Manager 即可。",
                "Q: 运行期间为什么要熄屏？" to
                    "A: 屏幕刷新会产生显示驱动 work，可能与 exploit 注入的内核 work 竞争，" +
                    "是概率性崩溃的最大来源。熄屏后显示驱动停止，成功率显著提高。",
                "Q: KernelSU Manager 显示“未安装”怎么办？" to
                    "A: 驱动已加载但 Manager 首次检测可能失败。强制停止 Manager 后重新打开，" +
                    "应显示“工作中 <LKM> [越狱模式]”。",
                "Q: 这个方案会熔断 KNOX 吗？" to
                    "A: 不会。本方案通过内核漏洞（CVE-2026-43499）免解锁提权，不刷 bootloader，" +
                    "KNOX e-fuse 状态保持原样（设置页可查看本机状态）。" +
                    "仅当设备之前已经解锁熔断（0x1）时无法恢复；Secure Folder 等 KNOX 功能" +
                    "在 root 后需用 KnoxPatch 恢复，与本方案是否熔断无关。",
                "Q: KNOX 状态检测准吗？重启后会变吗？" to
                    "A: 本 App 读取的是开机时 bootloader 写入的 ro.boot.warranty_bit（实时读取）。" +
                    "KNOX e-fuse 是硬件一次性状态：熔断不可逆，重启/刷机都不会改变。" +
                    "KnoxPatch 之类的模块只 hook 应用层检测 API，不修改该属性，所以显示的是真实值；" +
                    "若安装过 resetprop 类覆盖属性的模块，显示值可能被改，但硬件真实状态不变。",
                "Q: KnoxPatch / Secure Folder 怎么用？" to
                    "A: 在 KernelSU Manager 安装 Zygisk-Next 和 LSPosed 模块，安装 KnoxPatch APK，" +
                    "在 LSPosed 里启用后重启 Zygote，打开 KnoxPatch 勾选 Secure Folder 即可。",
                "Q: 卸载/恢复原样？" to
                    "A: 无需卸载——重启手机即恢复原状（模块文件保留在 /data/adb，不影响正常使用）。",
            ),
        )
        Spacer(Modifier.height(16.dp))

        // ---- 免责声明 ----
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("免责声明", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "本工具仅用于安全研究与自有设备维护。使用内核漏洞存在导致" +
                        "系统崩溃/数据丢失的风险，使用者需自行承担一切后果。请勿用于非法用途。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Text(
            "RootMyS9280 · github.com/nanoturtle1145/root-my-s9280\n基于 CVE-2026-43499 安全研究 · GPL-3.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconContainer(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconContainer(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 圆角图标容器（美化） */
@Composable
private fun IconContainer(icon: ImageVector) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            rows.forEachIndexed { index, (k, v) ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = k,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.32f),
                    )
                    Text(
                        text = v,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.68f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqCard(faqs: List<Pair<String, String>>) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            faqs.forEachIndexed { index, (q, a) ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Text(
                    text = q,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = a,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LicenseDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

// ---------------------------------------------------------------- 工具函数

/** 从 assets/licenses 读取许可原文 */
private fun loadLicense(context: Context, name: String): String = try {
    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
} catch (e: Exception) {
    "无法加载许可文本: ${e.message}"
}

/** 打开外部链接（浏览器） */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接: $url", Toast.LENGTH_SHORT).show()
    }
}

/** 读取系统属性（无需 root），失败返回 null */
private fun readProp(name: String): String? = try {
    val process = Runtime.getRuntime().exec(arrayOf("getprop", name))
    val value = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    value.ifBlank { null }
} catch (_: Exception) {
    null
}

// ---------------------------------------------------------------- 日志着色

@Composable
private fun logLineColor(line: String): Color {
    val t = line.trim()
    return when {
        t.startsWith("✔") || t.startsWith("🎉") ||
            t.contains("done=1 root=1") || t.contains("retval=0 socket=1") ||
            t.contains("slide-kaslr-ok") -> Color(0xFF4CAF50)

        t.startsWith("✗") || t.startsWith("[-]") ||
            t.contains("Failed") || t.contains("失败") || t.contains("error") ||
            t.contains("错误") || t.contains("Permission denied") -> Color(0xFFEF5350)

        t.startsWith("[+]") -> Color(0xFF66BB6A)
        t.startsWith("[*]") -> Color(0xFFFFB74D)
        t.startsWith("[") || t.startsWith("◆") || t.startsWith("⚠") ->
            MaterialTheme.colorScheme.primary

        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
