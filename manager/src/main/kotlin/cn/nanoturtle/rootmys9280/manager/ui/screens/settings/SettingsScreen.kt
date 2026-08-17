package cn.nanoturtle.rootmys9280.manager.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.BuildConfig
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorMono

private const val PREFS_SETTINGS = "settings"
private const val KEY_AUTO_SCREEN_OFF = "auto_screen_off"
private const val KEY_BRIEF_LOG = "brief_log"

/** 分组卡片里的行用透明容器色，避免 ListItem 在 Card 内再叠一层色块。 */
private val cardRowColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/**
 * 设置页：运行（自动熄屏 / 日志模式）+ 关于（检查更新 / 项目主页）+ 版本信息。
 * 纯静态 UI，偏好直接读写 SharedPreferences("settings")，无需 ViewModel。
 */
@Composable
fun SettingsScreen(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val prefs =
        remember(context) {
            context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        }
    var autoScreenOff by remember {
        mutableStateOf(prefs.getBoolean(KEY_AUTO_SCREEN_OFF, true))
    }
    var briefLog by remember {
        mutableStateOf(prefs.getBoolean(KEY_BRIEF_LOG, false))
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
        }

        item {
            SectionLabel("运行")
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = autoScreenOff,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                autoScreenOff = enabled
                                prefs.edit().putBoolean(KEY_AUTO_SCREEN_OFF, enabled).apply()
                            },
                        ),
                    leadingContent = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                    supportingContent = {
                        Text("运行期间自动熄灭屏幕，降低内核竞态概率")
                    },
                    trailingContent = { Switch(checked = autoScreenOff, onCheckedChange = null) },
                    colors = cardRowColors,
                ) { Text("自动熄屏") }
                HorizontalDivider()
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = briefLog,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                briefLog = enabled
                                prefs.edit().putBoolean(KEY_BRIEF_LOG, enabled).apply()
                            },
                        ),
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null)
                    },
                    supportingContent = { Text("运行日志只显示摘要行，减少刷屏") },
                    trailingContent = { Switch(checked = briefLog, onCheckedChange = null) },
                    colors = cardRowColors,
                ) { Text("日志模式") }
            }
        }

        item {
            SectionLabel("关于")
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier =
                        Modifier.clickable {
                            onOpenUrl("https://github.com/NanoTurtle1145/root-my-s9280/releases")
                        },
                    leadingContent = { Icon(Icons.Rounded.Update, contentDescription = null) },
                    supportingContent = { Text("前往 GitHub Releases 查看新版本") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                    colors = cardRowColors,
                ) { Text("检查更新") }
                HorizontalDivider()
                ListItem(
                    modifier =
                        Modifier.clickable {
                            onOpenUrl("https://github.com/NanoTurtle1145/root-my-s9280")
                        },
                    leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
                    supportingContent = { Text("源码、Issues 与使用说明") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                    colors = cardRowColors,
                ) { Text("项目主页") }
            }
        }

        item {
            Text(
                text = "版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = VectorMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
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
