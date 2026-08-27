package cn.nanoturtle.rootmys9280.manager.ui.screens.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import cn.nanoturtle.rootmys9280.manager.BuildConfig
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorMono

/** 分组卡片里的行用透明容器色，避免 ListItem 在 Card 内再叠一层色块。 */
private val cardRowColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** 一条常见问题。 */
private data class Faq(val question: String, val answer: String)

/** 常见问题列表：文案来自字符串资源，随系统语言切换。 */
@Composable
private fun faqList(): List<Faq> =
    listOf(
        Faq(
            question = stringResource(R.string.about_faq_knox_q),
            answer = stringResource(R.string.about_faq_knox_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_devices_q),
            answer = stringResource(R.string.about_faq_devices_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_manager_q),
            answer = stringResource(R.string.about_faq_manager_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_retry_q),
            answer = stringResource(R.string.about_faq_retry_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_restart_q),
            answer = stringResource(R.string.about_faq_restart_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_untested_q),
            answer = stringResource(R.string.about_faq_untested_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_adb_q),
            answer = stringResource(R.string.about_faq_adb_a),
        ),
        Faq(
            question = stringResource(R.string.about_faq_uninstall_q),
            answer = stringResource(R.string.about_faq_uninstall_a),
        ),
    )

/**
 * 关于页：应用图标与版本、设备信息、源代码与许可、常见问题、免责声明。
 * 纯静态 UI，无需 ViewModel。
 */
@Composable
fun AboutScreen(
    onOpenUrl: (String) -> Unit,
    onOpenDonate: () -> Unit = {},
) {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    val licenseFallback = stringResource(R.string.about_license_load_failed)
    val licenseText = remember(context) {
        runCatching {
            context.resources.openRawResource(R.raw.gpl_v3_license).bufferedReader().readText()
        }.getOrDefault(licenseFallback)
    }
    // 运行时读取已安装的应用图标，避免依赖具体 mipmap 资源名。
    val appIcon =
        remember(context) {
            runCatching {
                val info = context.packageManager.getApplicationInfo(context.packageName, 0)
                info.loadIcon(context.packageManager).toBitmap().asImageBitmap()
            }.getOrNull()
        }

    if (showLicense) {
        LicenseDialog(
            title = stringResource(R.string.about_license_title),
            text = licenseText,
            onDismiss = { showLicense = false },
        )
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        item {
            cn.nanoturtle.rootmys9280.manager.ui.components.BannerHeader(
                title = stringResource(R.string.about_screen_title),
                subtitle = stringResource(R.string.about_screen_subtitle),
                modifier = Modifier.padding(top = 24.dp),
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
                Text("RootMyS24", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = VectorMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.about_section_device))
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Rounded.Smartphone, contentDescription = null)
                    },
                    supportingContent = { Text(Build.MODEL, style = VectorMono) },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.about_device_model)) }
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Android, contentDescription = null) },
                    supportingContent = {
                        Text("Android ${Build.VERSION.RELEASE}", style = VectorMono)
                    },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.about_system_version)) }
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                    supportingContent = { Text(Build.FINGERPRINT, style = VectorMono) },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.about_build_fingerprint)) }
            }
        }

        item {
            SectionLabel(stringResource(R.string.about_section_source))
            Card(modifier = Modifier.fillMaxWidth()) {
                LicenseRow(
                    title = "RootMyS24 (GPL-3.0)",
                    url = "https://github.com/NanoTurtle1145/root-my-s24",
                    onOpenUrl = onOpenUrl,
                    onOpenLicense = { showLicense = true },
                )
                HorizontalDivider()
                LicenseRow(
                    title = "KernelSU (GPL-2.0)",
                    url = "https://github.com/tiann/KernelSU",
                    onOpenUrl = onOpenUrl,
                    onOpenLicense = { showLicense = true },
                )
                HorizontalDivider()
                LicenseRow(
                    title = "LSPosed (GPL-3.0)",
                    url = "https://github.com/LSPosed/LSPosed",
                    onOpenUrl = onOpenUrl,
                    onOpenLicense = { showLicense = true },
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.about_section_faq))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    faqList().forEachIndexed { index, faq ->
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
            SectionLabel(stringResource(R.string.about_section_support))
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDonate() },
            ) {
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Rounded.VolunteerActivism,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.about_support_title)) },
                    supportingContent = { Text(stringResource(R.string.about_support_body)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                    colors = cardRowColors,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

/** 一行可点击的开源许可条目：点击弹内嵌许可证全文；也可点击链接图标打开源码页。 */
@Composable
private fun LicenseRow(
    title: String,
    url: String,
    onOpenUrl: (String) -> Unit,
    onOpenLicense: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpenLicense),
        leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.clickable { onOpenUrl(url) },
            )
        },
        colors = cardRowColors,
    ) { Text(title) }
}

/** 内嵌许可证全文对话框（滚动查看，离线可用）。 */
@Composable
private fun LicenseDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = text,
                style = VectorMono,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_license_close))
            }
        },
    )
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
