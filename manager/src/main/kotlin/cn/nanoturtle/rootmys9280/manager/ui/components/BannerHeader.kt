package cn.nanoturtle.rootmys9280.manager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.ui.ambience.AmbienceKind
import cn.nanoturtle.rootmys9280.ui.ambience.AmbientHeader

/**
 * 页面顶部的动态 banner（Vector ambience 同款）：
 * 大标题 + 副标题，背后是当前设置的动态背景（迷宫/电路/矩阵/雪花）。
 */
@Composable
fun BannerHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val ambienceKey by ServiceLocator.settings.headerAmbience.collectAsStateWithLifecycle()

    AmbientHeader(
        kind = AmbienceKind.from(ambienceKey),
        tint = MaterialTheme.colorScheme.primary,
        settings = VectorAmbienceSettings,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 20.dp, horizontal = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
