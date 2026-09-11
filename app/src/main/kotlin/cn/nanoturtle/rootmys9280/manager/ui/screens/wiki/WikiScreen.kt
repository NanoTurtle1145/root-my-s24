package cn.nanoturtle.rootmys9280.manager.ui.screens.wiki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.R

/**
 * 必读指南：临时 Root 的原理、重启即恢复、以及会导致变砖的误操作。
 *
 * 同一个内容有两个入口——首次启动引导时强制展示一次，之后随时可从「关于」页再看。
 * 因此正文拆成 [WikiContent]（纯内容、可嵌进任何容器），
 * [WikiScreen] 只是给它套一个带返回按钮的页面壳。
 */
@Composable
fun WikiScreen(onNavigateBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.donate_back))
                }
            }
        }
        item { WikiContent() }
    }
}

/** 指南正文本体（不含页面壳），供引导页与独立页面共用。 */
@Composable
fun WikiContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        cn.nanoturtle.rootmys9280.manager.ui.components.BannerHeader(
            title = stringResource(R.string.wiki_screen_title),
            subtitle = stringResource(R.string.wiki_subtitle),
            modifier = Modifier.padding(top = 8.dp),
        )

        // 1. 临时 root 不等于解锁 bootloader —— 这是最多人误解的一点，放最前面。
        WikiCard(
            icon = Icons.Rounded.LockOpen,
            title = stringResource(R.string.wiki_temp_title),
            body = stringResource(R.string.wiki_temp_body),
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Spacer(Modifier.height(8.dp))

        // 2. 重启即恢复，说明它是可逆的。
        WikiCard(
            icon = Icons.Rounded.RestartAlt,
            title = stringResource(R.string.wiki_reboot_title),
            body = stringResource(R.string.wiki_reboot_body),
            container = MaterialTheme.colorScheme.surfaceContainer,
            onContainer = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        // 3. 警告：把 KSU 永久装进系统后重启变砖的坑。
        WikiCard(
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.wiki_warn_title),
            body = stringResource(R.string.wiki_warn_body),
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
        )

        Spacer(Modifier.height(8.dp))

        // 4. 使用步骤。
        WikiCard(
            icon = Icons.Rounded.Checklist,
            title = stringResource(R.string.wiki_steps_title),
            body = stringResource(R.string.wiki_steps_body),
            container = MaterialTheme.colorScheme.surfaceContainer,
            onContainer = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.wiki_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

/** 指南里的一个板块：图标 + 标题 + 正文，整块一个圆角容器。 */
@Composable
private fun WikiCard(
    icon: ImageVector,
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}
