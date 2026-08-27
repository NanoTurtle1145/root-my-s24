package cn.nanoturtle.rootmys9280.manager.ui.screens.logs

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
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown) { line ->
                    val container =
                        if (line.summary) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else Color.Transparent
                    Surface(
                        color = container,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
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
}
