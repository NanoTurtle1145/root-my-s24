package cn.nanoturtle.rootmys9280.manager.ui.screens.announce

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.rootmy.Announcer
import cn.nanoturtle.rootmys9280.manager.ui.components.AnnouncerCard
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 公告列表页：主页只放最靠前的两条 + 「查看全部」入口，点进来在这里看全部。
 *
 * 数据与安全约束都来自 [Announcer]（链接只放行 http/https、动作走白名单、
 * 拉取失败当无公告处理）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<Announcer.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // 列表页显示**全部**公告（含已在主页关闭的），它是"归档"而不是"未读列表" ——
    // 这样任何一条公告都不会因为误触关闭而彻底消失。
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { Announcer.feed(context).all }
        loading = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.announce_screen_title)) },
                actions = {
                    // 恢复入口：主页的叉叉会把公告逐条关掉，但服务端并不知道，
                    // 所以必须留一条"让它们回来"的路（清本地已关闭记录即可）
                    TextButton(
                        onClick = {
                            Announcer.clearDismissed(context)
                            loading = true
                            scope.launch {
                                items = withContext(Dispatchers.IO) { Announcer.feed(context).all }
                                loading = false
                            }
                        },
                    ) { Text(stringResource(R.string.announce_restore)) }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.announce_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                loading -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                items.isEmpty() -> {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        text = stringResource(R.string.announce_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }

                else -> AnnouncerCard(
                    items = items,
                    maxVisible = items.size,
                    onOpenItem = { /* 已在详情页 */ },
                    // 详情页是"归档"：不提供关闭，否则关掉后本页反而看不到
                    onDismiss = null,
                    onOpenLink = { url ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                )
                            )
                        }
                    },
                    // 详情页里动作只保留可执行的（未知动作由白名单挡掉）
                    onAction = { /* no-op：本页不消费动作 */ },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
