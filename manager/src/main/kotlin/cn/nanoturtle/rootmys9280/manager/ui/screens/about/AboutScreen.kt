package cn.nanoturtle.rootmys9280.manager.ui.screens.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import cn.nanoturtle.rootmys9280.manager.BuildConfig
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorMono

/** 分组卡片里的行用透明容器色，避免 ListItem 在 Card 内再叠一层色块。 */
private val cardRowColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** 一条常见问题。 */
private data class Faq(val question: String, val answer: String)

private val FAQS =
    listOf(
        Faq(
            question = "会熔断 KNOX 吗？",
            answer = "不会。免解锁提权不刷 bootloader，KNOX 状态保持原样。",
        ),
        Faq(
            question = "为什么需要多试几次？",
            answer = "exploit 通过内核内存破坏提权，概率性成功，失败后重启重试即可。",
        ),
        Faq(
            question = "重启后要重新 Root 吗？",
            answer = "是。bootloader 锁定，每次重启后需要重新运行免解锁 Root。",
        ),
    )

/**
 * 关于页：应用图标与版本、设备信息、源代码与许可、常见问题、免责声明。
 * 纯静态 UI，无需 ViewModel。
 */
@Composable
fun AboutScreen(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    // 运行时读取已安装的应用图标，避免依赖具体 mipmap 资源名。
    val appIcon =
        remember(context) {
            runCatching {
                val info = context.packageManager.getApplicationInfo(context.packageName, 0)
                info.loadIcon(context.packageManager).toBitmap().asImageBitmap()
            }.getOrNull()
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                text = "关于",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("RootMyS9280", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = VectorMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SM-S9280 国行免解锁 root · 基于 CVE-2026-43499 安全研究",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            SectionLabel("设备信息")
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Rounded.Smartphone, contentDescription = null)
                    },
                    supportingContent = { Text(Build.MODEL, style = VectorMono) },
                    colors = cardRowColors,
                ) { Text("设备型号") }
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Android, contentDescription = null) },
                    supportingContent = {
                        Text("Android ${Build.VERSION.RELEASE}", style = VectorMono)
                    },
                    colors = cardRowColors,
                ) { Text("系统版本") }
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                    supportingContent = { Text(Build.FINGERPRINT, style = VectorMono) },
                    colors = cardRowColors,
                ) { Text("构建指纹") }
            }
        }

        item {
            SectionLabel("源代码与许可")
            Card(modifier = Modifier.fillMaxWidth()) {
                LicenseRow(
                    title = "RootMyS9280 (GPL-3.0)",
                    url = "https://github.com/NanoTurtle1145/root-my-s9280",
                    onOpenUrl = onOpenUrl,
                )
                HorizontalDivider()
                LicenseRow(
                    title = "KernelSU (GPL-2.0)",
                    url = "https://github.com/tiann/KernelSU",
                    onOpenUrl = onOpenUrl,
                )
                HorizontalDivider()
                LicenseRow(
                    title = "LSPosed (GPL-3.0)",
                    url = "https://github.com/LSPosed/LSPosed",
                    onOpenUrl = onOpenUrl,
                )
            }
        }

        item {
            SectionLabel("常见问题")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    FAQS.forEachIndexed { index, faq ->
                        if (index > 0) {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        }
                        Text(
                            text = "Q: ${faq.question}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "A: ${faq.answer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text =
                    "免责声明：本应用仅供安全研究与个人学习使用。内核内存破坏提权存在不确定性，使用后果请自行承担；请遵守当地法律法规，勿用于非法用途。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

/** 一行可点击的开源许可条目。 */
@Composable
private fun LicenseRow(title: String, url: String, onOpenUrl: (String) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onOpenUrl(url) },
        leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
        trailingContent = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
        colors = cardRowColors,
    ) { Text(title) }
}

/** 分组标题：与 Vector 各页的 section 标题一致的样式。 */
@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
