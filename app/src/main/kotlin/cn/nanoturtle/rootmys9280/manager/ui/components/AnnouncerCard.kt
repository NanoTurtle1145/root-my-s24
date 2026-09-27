package cn.nanoturtle.rootmys9280.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.rootmy.Announcer

/**
 * 公告卡片（announcer）。
 *
 * 只做三件事：显示文案、打开 http/https 链接、执行白名单动作。
 * **不接受服务端下发的任意行为** —— 见 [Announcer] 顶部的约束说明。
 */
@Composable
fun AnnouncerCard(
    items: List<Announcer.Item>,
    onDismiss: (Announcer.Item) -> Unit,
    onOpenLink: (String) -> Unit,
    onAction: (Announcer.Item) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 2,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.take(maxVisible).forEach { item ->
            val (icon, container, content) = styleOf(item.level)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = container),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = content,
                        )
                        if (item.body.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = content,
                                // 长公告折到 6 行以内：主页是入口，不是阅读器
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val hasLink = item.link != null
                        val hasAction = item.action != Announcer.Action.NONE &&
                            !(item.action == Announcer.Action.DISMISS)
                        if (hasLink || hasAction) {
                            Spacer(Modifier.height(2.dp))
                            Row {
                                if (hasLink) {
                                    TextButton(onClick = { onOpenLink(item.link!!) }) {
                                        Text(item.linkText)
                                    }
                                }
                                if (hasAction) {
                                    TextButton(onClick = { onAction(item) }) {
                                        Text(item.actionText)
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onDismiss(item) }) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = content)
                    }
                }
            }
        }
    }
}

/** 档位 → 图标/底色/前景色。低调为主：主页上不该被公告抢走注意力。 */
private fun styleOf(level: Announcer.Level): Triple<ImageVector, Color, Color> = when (level) {
    Announcer.Level.WARN -> Triple(
        Icons.Rounded.Error,
        Color(0xFFFFF3E0),
        Color(0xFF8A4B00),
    )
    Announcer.Level.CRITICAL -> Triple(
        Icons.Rounded.Error,
        Color(0xFFFFEBEE),
        Color(0xFF9B1C1C),
    )
    Announcer.Level.UPDATE -> Triple(
        Icons.Rounded.SystemUpdate,
        Color(0xFFE8F0FE),
        Color(0xFF1B3A6B),
    )
    Announcer.Level.INFO -> Triple(
        Icons.Rounded.Info,
        Color(0xFFF1F3F6),
        Color(0xFF33383D),
    )
}
