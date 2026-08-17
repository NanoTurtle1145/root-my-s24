package cn.nanoturtle.rootmys9280

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 免解锁 Root 独立页（照搬 KSU 移植的 RootFlowScreen）：
 * 顶部返回栏 + 开始按钮 + 设置卡（自动熄屏/KNOX/导出日志）+ 运行日志（详细/粗略）。
 */
@Composable
fun RootFlowScreen(onBack: () -> Unit, vm: RootViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "免解锁 Root",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回", tint = colorScheme.onBackground)
                    }
                },
            )
        },
        popupHost = { },
    ) { innerPadding ->
        RootFlowContent(
            vm = vm,
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun RootFlowContent(
    vm: RootViewModel,
    state: RootViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    var brief by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.refreshKnox()
    }

    val shown = if (brief) state.logLines.filter { it.summary } else state.logLines
    LaunchedEffect(shown.size, brief) {
        if (shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .padding(horizontal = 12.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { vm.start() },
                            enabled = !state.busy,
                        ) {
                            Text(if (state.busy) "运行中..." else "开始 Root")
                        }
                        Spacer(Modifier.padding(start = 12.dp))
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        }
                        if (state.rooted) {
                            Text("✓ root 完成", color = Color(0xFF4CAF50))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "免解锁提权：不刷 bootloader、不熔断 KNOX。运行期间建议熄屏。",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            SettingsCard(
                vm = vm,
                state = state,
                exportResult = exportResult,
                onExport = {
                    scope.launch { exportResult = vm.dumpLog() }
                },
                onExportResultShown = { exportResult = null },
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("运行日志", fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                TextButton(
                    text = if (brief) "详细" else "粗略",
                    onClick = { brief = !brief },
                )
            }
        }

        val shown = if (brief) state.logLines.filter { it.summary } else state.logLines
        items(shown) { line ->
            Text(
                text = line.text,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (line.summary) FontWeight.Bold else FontWeight.Normal,
                color = if (line.summary) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    vm: RootViewModel,
    state: RootViewModel.UiState,
    exportResult: String?,
    onExport: () -> Unit,
    onExportResultShown: () -> Unit,
) {
    LaunchedEffect(exportResult) {
        if (exportResult != null) {
            delay(6_000)
            onExportResultShown()
        }
    }
    Card(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动熄屏", fontSize = 15.sp)
                    Text(
                        "运行期间自动熄灭屏幕，降低内核竞态概率",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                Switch(
                    checked = vm.autoScreenOff,
                    onCheckedChange = { vm.setAutoScreenOff(it) },
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("KNOX 状态", fontSize = 15.sp)
                    Text(
                        state.knoxState.ifBlank { "查询中...（需 Shizuku）" },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导出日志", fontSize = 15.sp)
                    Text(
                        exportResult ?: "保存完整运行日志到系统下载目录",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                TextButton(
                    text = "导出",
                    onClick = onExport,
                )
            }
        }
    }
}
