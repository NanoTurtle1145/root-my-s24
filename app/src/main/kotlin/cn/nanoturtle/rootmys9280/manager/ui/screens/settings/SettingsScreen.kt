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
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsRemote
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.BuildConfig
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorMono

private const val PREFS_SETTINGS = "settings"
private const val KEY_AUTO_SCREEN_OFF = "auto_screen_off"
private const val KEY_BRIEF_LOG = "brief_log"
private const val KEY_AUTO_SAVE_LOG = "auto_save_log"
private const val KEY_ADB_WIRELESS_ENABLED = "adb_wireless_enabled"

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
    var autoSaveLog by remember {
        mutableStateOf(prefs.getBoolean(KEY_AUTO_SAVE_LOG, true))
    }
    var adbWirelessEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_ADB_WIRELESS_ENABLED, false))
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        item {
            cn.nanoturtle.rootmys9280.manager.ui.components.BannerHeader(
                title = stringResource(R.string.settings_screen_title),
                subtitle = stringResource(R.string.settings_screen_subtitle),
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        item {
            SectionLabel(stringResource(R.string.settings_section_run))
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
                        Text(stringResource(R.string.settings_auto_screen_off_summary))
                    },
                    trailingContent = { Switch(checked = autoScreenOff, onCheckedChange = null) },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.settings_auto_screen_off)) }
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
                    supportingContent = { Text(stringResource(R.string.settings_brief_log_summary)) },
                    trailingContent = { Switch(checked = briefLog, onCheckedChange = null) },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.settings_brief_log)) }
                HorizontalDivider()
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = autoSaveLog,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                autoSaveLog = enabled
                                prefs.edit().putBoolean(KEY_AUTO_SAVE_LOG, enabled).apply()
                            },
                        ),
                    leadingContent = {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                    },
                    supportingContent = { Text(stringResource(R.string.settings_auto_save_log_summary)) },
                    trailingContent = { Switch(checked = autoSaveLog, onCheckedChange = null) },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.settings_auto_save_log)) }
                HorizontalDivider()
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = adbWirelessEnabled,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                adbWirelessEnabled = enabled
                                prefs.edit().putBoolean(KEY_ADB_WIRELESS_ENABLED, enabled).apply()
                                // 同步进程级单例 VM 的 StateFlow，主页据此显示/隐藏无线调试控件
                                runCatching {
                                    ServiceLocator.rootViewModel.setAdbWirelessEnabled(enabled)
                                }
                            },
                        ),
                    leadingContent = {
                        Icon(Icons.Rounded.SettingsRemote, contentDescription = null)
                    },
                    supportingContent = { Text(stringResource(R.string.settings_adb_wireless_summary)) },
                    trailingContent = { Switch(checked = adbWirelessEnabled, onCheckedChange = null) },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.settings_adb_wireless)) }
            }
        }

        item {
            SectionLabel(stringResource(R.string.settings_section_about))
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier =
                        Modifier.clickable {
                            onOpenUrl("https://github.com/NanoTurtle1145/root-my-s24/releases")
                        },
                    leadingContent = { Icon(Icons.Rounded.Update, contentDescription = null) },
                    supportingContent = { Text(stringResource(R.string.settings_check_update_summary)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.settings_check_update)) }
                HorizontalDivider()
                ListItem(
                    modifier =
                        Modifier.clickable {
                            onOpenUrl("https://github.com/NanoTurtle1145/root-my-s24")
                        },
                    leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
                    supportingContent = { Text(stringResource(R.string.settings_project_home_summary)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                    colors = cardRowColors,
                ) { Text(stringResource(R.string.settings_project_home)) }
            }
        }

        item {
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
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
