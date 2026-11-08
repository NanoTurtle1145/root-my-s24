package cn.nanoturtle.rootmys9280.manager.ui.screens.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import cn.nanoturtle.rootmys9280.manager.rootmy.RunLogStore

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
    var history by remember { mutableStateOf(RunLogStore.list(context)) }
    var historyOpen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(lastLogCode) { history = RunLogStore.list(context) }

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
            TextButton(onClick = { vm.clearLog() }) { Text(stringResource(R.string.logs_clear)) }
        }

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
          Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                  Row(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      Text(
                          text = stringResource(R.string.logs_history_title, history.size),
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Spacer(Modifier.weight(1f))
                      TextButton(onClick = {
                          history = RunLogStore.list(context)
                          historyOpen = !historyOpen
                      }) {
                          Text(stringResource(if (historyOpen) R.string.logs_history_close else R.string.logs_history_open))
                      }
                  }
                  if (historyOpen) {
                      if (history.isEmpty()) {
                          Text(
                              text = stringResource(R.string.logs_history_empty),
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                          )
                      } else {
                          history.take(10).forEach { e ->
                              Row(
                                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                  verticalAlignment = Alignment.CenterVertically,
                              ) {
                                  Text(
                                      text = e.code,
                                      fontFamily = FontFamily.Monospace,
                                      fontWeight = FontWeight.Medium,
                                      fontSize = 13.sp,
                                      color = MaterialTheme.colorScheme.primary,
                                  )
                                  Spacer(Modifier.width(10.dp))
                                  Text(
                                      text = e.timeLabel() + "  " + e.outcome,
                                      style = MaterialTheme.typography.labelSmall,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                      modifier = Modifier.weight(1f),
                                  )
                                  TextButton(onClick = { viewing = e.code to (RunLogStore.read(context, e.code) ?: "") }) {
                                      Text(stringResource(R.string.logs_history_view))
                                  }
                              }
                          }
                      }
                  }
              }
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
    }
    viewing?.let { (code, text) ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(stringResource(R.string.logs_history_detail, code)) },
            text = {
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("run-log-code", code))
                    android.widget.Toast.makeText(context, context.getString(R.string.logs_code_copied, code), android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.logs_copy_code)) }
            },
            dismissButton = {
                TextButton(onClick = { viewing = null }) { Text(stringResource(R.string.logs_history_close)) }
            },
        )
    }

}
