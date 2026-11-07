package cn.nanoturtle.rootmys9280.manager.ui.screens.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.ui.components.MarkdownBlockView
import cn.nanoturtle.rootmys9280.manager.ui.components.parseMarkdown

/**
 * 隐私政策页：直接渲染打包进 assets 的 `privacy.md`。
 *
 * 内容与仓库根目录的 `docs/PRIVACY.md` 同一份（构建前由脚本/手工同步），
 * 这样「App 里的政策」和「GitHub 上的政策」不会各写一套、渐渐对不上。
 * 链接交给调用方处理（App 内置 WebView 或外部浏览器）。
 */
@Composable
fun PrivacyScreen(onNavigateBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val markdown = remember {
        runCatching {
                context.assets.open("privacy.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            .getOrDefault("")
    }
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.privacy_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.donate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(blocks) { index, block ->
                MarkdownBlockView(
                    block = block,
                    onLink = onOpenUrl,
                    modifier = Modifier.fillMaxSize(),
                )
                if (index == blocks.lastIndex) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
