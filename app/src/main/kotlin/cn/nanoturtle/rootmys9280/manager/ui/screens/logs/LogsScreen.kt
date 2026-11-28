package cn.nanoturtle.rootmys9280.manager.ui.screens.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.rootmy.RootViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog

/**
 * 运行日志 tab：完整展示免解锁 Root 流程输出（详细/粗略切换、导出、清空）。
 */
@Composable
fun LogsScreen(
    vm: RootViewModel = ServiceLocator.rootViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var brief by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastLogCode by vm.lastLogCode.collectAsStateWithLifecycle()
    var showSoftReboot by remember { mutableStateOf(false) }

    val shown = if (brief) state.logLines.filter { it.summary } else state.logLines
    LaunchedEffect(shown.size, brief) {
        if (shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        cn.nanoturtle.rootmys9280.manager.ui.components.BannerHeader(
            title = stringResource(R.string.logs_title),
            subtitle = stringResource(R.string.logs_subtitle),
            modifier = Modifier.padding(top = 24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { brief = !brief }) {
                Text(if (brief) stringResource(R.string.logs_detail) else stringResource(R.string.logs_brief))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    scope.launch {
                        val result = vm.dumpLog()
                        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
                    }
                },
            ) { Text(stringResource(R.string.logs_export)) }
            TextButton(
                onClick = {
                    scope.launch {
                        val result = vm.uploadLog(RootViewModel.SOURCE_MANUAL)
                        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG)
                            .show()
                    }
                },
            ) { Text(stringResource(R.string.logs_upload)) }
            TextButton(
                onClick = {
                    val code = lastLogCode
                    if (code == null) {
                        android.widget.Toast.makeText(context, context.getString(R.string.logs_code_none), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("run-log-code", code))
                        android.widget.Toast.makeText(context, context.getString(R.string.logs_code_copied, code), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            ) { Text(stringResource(R.string.logs_copy_code)) }
            TextButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, RunLogHistoryActivity::class.java),
                    )
                },
            ) { Text(stringResource(R.string.logs_history_button)) }
            TextButton(onClick = { vm.clearLog() }) { Text(stringResource(R.string.logs_clear)) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
        if (shown.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (brief) {
                        stringResource(R.string.logs_empty_brief)
                    } else {
                        stringResource(R.string.logs_empty)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(shown) { index, line ->
                    // 大小圆角分组：首行上大圆角、末行下大圆角、中间收小
                    val isLast = index == shown.lastIndex
                    val container =
                        if (line.summary) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    Surface(
                        color = container,
                        shape =
                            RoundedCornerShape(
                                topStart = if (index == 0) 20.dp else 4.dp,
                                topEnd = if (index == 0) 20.dp else 4.dp,
                                bottomStart = if (isLast) 20.dp else 4.dp,
                                bottomEnd = if (isLast) 20.dp else 4.dp,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = line.text,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (line.summary) FontWeight.Bold else FontWeight.Normal,
                            color = if (line.summary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        val canSoftReboot = state.rooted &&
            vm.ksuVersionEffective(vm.firmwareVersion).startsWith("3.3")
        androidx.compose.animation.AnimatedVisibility(
            visible = canSoftReboot,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) +
                androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
                androidx.compose.animation.fadeOut(),
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    // 再兜一层：脚手架在浮动导航模式下不消费底部 inset（外层 NavDisplay 已补，
                    // 正常会消费成 0）；写成局部规则后，这个按钮不再依赖外壳的实现。
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.clickable { showSoftReboot = true },
            ) {
                Text(
                    text = stringResource(R.string.logs_soft_reboot),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        }
        if (showSoftReboot) {
            AlertDialog(
                onDismissRequest = { showSoftReboot = false },
                title = { Text(stringResource(R.string.logs_soft_reboot_title)) },
                text = { Text(stringResource(R.string.logs_soft_reboot_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSoftReboot = false
                            scope.launch { vm.softRebootForModules() }
                        },
                    ) { Text(stringResource(R.string.logs_soft_reboot_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSoftReboot = false }) {
                        Text(stringResource(R.string.logs_soft_reboot_cancel))
                    }
                },
            )
        }
    }

}
