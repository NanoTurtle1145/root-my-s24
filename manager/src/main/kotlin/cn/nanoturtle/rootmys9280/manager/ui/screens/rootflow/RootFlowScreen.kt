package cn.nanoturtle.rootmys9280.manager.ui.screens.rootflow

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.rootmy.RootViewModel
import cn.nanoturtle.rootmys9280.manager.ui.components.VectorAmbienceSettings
import cn.nanoturtle.rootmys9280.ui.StatusHeader
import cn.nanoturtle.rootmys9280.ui.StatusTone
import cn.nanoturtle.rootmys9280.ui.ambience.AmbienceKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 免解锁 Root tab 页（Material3 版）：
 * 开始按钮 + 设置卡（自动熄屏/KNOX/导出日志）+ 运行日志（详细/粗略）。
 */
@Composable
fun RootFlowScreen(
    vm: RootViewModel = ServiceLocator.rootViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    RootFlowContent(vm = vm, state = state)
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
            .padding(horizontal = 16.dp),
    ) {
        item {
            // Vector 同款完整状态 banner：动态背景 + 品牌 + 状态徽章（呼吸动画）+ 详情行
            val ambienceKey by ServiceLocator.settings.headerAmbience.collectAsStateWithLifecycle()
            val tone: StatusTone =
                when {
                    state.busy -> StatusTone.Neutral
                    state.rooted -> StatusTone.Active
                    else -> StatusTone.Error
                }
            val statusWord =
                when {
                    state.busy -> stringResource(R.string.status_checking)
                    state.rooted -> stringResource(R.string.status_active)
                    else -> stringResource(R.string.status_inactive)
                }
            StatusHeader(
                brand = "RootMyS9280",
                statusWord = statusWord,
                tone = tone,
                ambience = AmbienceKind.from(ambienceKey),
                ambienceSettings = VectorAmbienceSettings,
                modifier = Modifier.padding(top = 24.dp),
                detail = { contentColor ->
                    Text(
                        text = state.knoxState.ifBlank { "SM-S9280 国行免解锁 root · 不刷机、不熔断 KNOX" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                },
            )
        }

        item {
            Card(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Button(
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("运行日志", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { brief = !brief },
                ) {
                    Text(if (brief) "详细" else "粗略")
                }
            }
        }

        items(shown) { line ->
            Text(
                text = line.text,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (line.summary) FontWeight.Bold else FontWeight.Normal,
                color = if (line.summary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("自动熄屏", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "运行期间自动熄灭屏幕，降低内核竞态概率",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("KNOX 状态", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.knoxState.ifBlank { "查询中...（需 Shizuku）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("导出日志", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        exportResult ?: "保存完整运行日志到系统下载目录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onExport,
                ) {
                    Text("导出")
                }
            }
        }
    }
}
