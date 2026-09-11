package cn.nanoturtle.rootmys9280.manager.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.manager.BuildConfig
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.rootmy.LogSharing
import cn.nanoturtle.rootmys9280.manager.rootmy.LogUploader
import cn.nanoturtle.rootmys9280.manager.rootmy.OnboardingPrefs
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorMono
import kotlinx.coroutines.launch

private const val PREFS_SETTINGS = "settings"
private const val KEY_AUTO_SCREEN_OFF = "auto_screen_off"
private const val KEY_BRIEF_LOG = "brief_log"
private const val KEY_AUTO_SAVE_LOG = "auto_save_log"
private const val KEY_ADB_WIRELESS_ENABLED = "adb_wireless_enabled"
private const val KEY_UNTESTED_PAYLOADS_ENABLED = "untested_payloads_enabled"
private const val KEY_DEBUG_MODE = "debug_mode"

/** 点版本号多少下开启调试模式 */
private const val DEBUG_TAPS = 7

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
    var untestedPayloadsEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_UNTESTED_PAYLOADS_ENABLED, false))
    }
    // 日志共享方式：引导页写过一次，这里读出来并在改动时回写。
    var logSharing by remember {
        mutableStateOf(OnboardingPrefs.logSharing(context))
    }
    // 调试模式：点版本号七下开启（与 Android 开发者选项同一套习惯）
    var debugMode by remember { mutableStateOf(prefs.getBoolean(KEY_DEBUG_MODE, false)) }
    var versionTaps by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val vm = ServiceLocator.rootViewModel
    // 文案在组合作用域取好，避免在点击回调里用 context.getString（配置变更时会拿到旧值）
    val rerunGuideMsg = stringResource(R.string.settings_debug_rerun_onboarding)
    val debugOnMsg = stringResource(R.string.settings_debug_on)
    val testingMsg = stringResource(R.string.settings_debug_testing)
    // 连点会快过重组，versionTaps 可能超过 DEBUG_TAPS；这里夹紧，提示不会出现负数
    val tapsRemaining = (DEBUG_TAPS - versionTaps).coerceAtLeast(0)
    val tapsLeftMsg = stringResource(R.string.settings_debug_taps_left, tapsRemaining)
    // 调试项的测试结果就地显示（不弹 Toast），所以放在状态里而不是 Toast 里
    var debugResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

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
            // 大小圆角分组：第一行上大圆角、中间收小、最后一行下大圆角；行间保留间距不粘连
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupedRow(
                    index = 0,
                    count = 5,
                ) {
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
                }
                GroupedRow(
                    index = 1,
                    count = 5,
                ) {
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
                }
                GroupedRow(
                    index = 2,
                    count = 5,
                ) {
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
                }
                GroupedRow(
                    index = 3,
                    count = 5,
                ) {
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
                GroupedRow(
                    index = 4,
                    count = 5,
                ) {
                    ListItem(
                        modifier =
                            Modifier.toggleable(
                                value = untestedPayloadsEnabled,
                                role = Role.Switch,
                                onValueChange = { enabled ->
                                    untestedPayloadsEnabled = enabled
                                    prefs.edit().putBoolean(KEY_UNTESTED_PAYLOADS_ENABLED, enabled).apply()
                                    // 同步进程级单例 VM 的 StateFlow，固件选择页据此过滤 untested 条目
                                    runCatching {
                                        ServiceLocator.rootViewModel.setUntestedPayloadsEnabled(enabled)
                                    }
                                },
                            ),
                        leadingContent = {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                        },
                        supportingContent = { Text(stringResource(R.string.settings_untested_payloads_summary)) },
                        trailingContent = { Switch(checked = untestedPayloadsEnabled, onCheckedChange = null) },
                        colors = cardRowColors,
                    ) { Text(stringResource(R.string.settings_untested_payloads)) }
                }
            }
        }

        item {
            SectionLabel(stringResource(R.string.settings_section_about))
            // 大小圆角分组：2 行 —— 第一行上大圆角，最后一行下大圆角
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupedRow(
                    index = 0,
                    count = 2,
                ) {
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
                }
                GroupedRow(
                    index = 1,
                    count = 2,
                ) {
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
        }

        item {
            SectionLabel(stringResource(R.string.settings_section_privacy))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 首次启动问过一次的选择，之后在这里随时能改。
                GroupedRow(index = 0, count = 1) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_log_sharing_summary))
                            },
                            colors = cardRowColors,
                        ) { Text(stringResource(R.string.settings_log_sharing)) }
                        LogSharing.entries.forEach { mode ->
                            LogSharingOption(
                                mode = mode,
                                selected = logSharing == mode,
                                onSelect = {
                                    logSharing = mode
                                    OnboardingPrefs.setLogSharing(context, mode)
                                },
                            )
                        }
                    }
                }
            }
        }

        // 调试分区只在「点版本号七下」之后出现，普通用户看不到这些危险/诊断入口。
        if (debugMode) {
            item {
                SectionLabel(stringResource(R.string.settings_section_debug))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupedRow(index = 0, count = 4) {
                        Column {
                            ListItem(
                                modifier =
                                    Modifier.clickable {
                                        if (testing) return@clickable
                                        // 结果就地显示，不弹 Toast：诊断信息要能停在那儿慢慢看，
                                        // Toast 一闪而过还得再点一次才能复现。
                                        testing = true
                                        debugResult = testingMsg
                                        scope.launch {
                                            debugResult = vm.testLogEndpoint()
                                            testing = false
                                        }
                                    },
                                leadingContent = {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                                },
                                supportingContent = {
                                    Text(LogUploader.endpoint(context), style = VectorMono)
                                },
                                colors = cardRowColors,
                            ) { Text(stringResource(R.string.settings_debug_test_db)) }
                            debugResult?.let { result ->
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                )
                            }
                        }
                    }
                    GroupedRow(index = 1, count = 4) {
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                            },
                            supportingContent = {
                                Text(LogUploader.installId(context), style = VectorMono)
                            },
                            colors = cardRowColors,
                        ) { Text(stringResource(R.string.settings_debug_install_id)) }
                    }
                    GroupedRow(index = 2, count = 4) {
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    OnboardingPrefs.resetOnboarding(context)
                                    debugResult = rerunGuideMsg
                                },
                            leadingContent = {
                                Icon(Icons.Rounded.MenuBook, contentDescription = null)
                            },
                            colors = cardRowColors,
                        ) { Text(stringResource(R.string.settings_debug_rerun_onboarding)) }
                    }
                    GroupedRow(index = 3, count = 4) {
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    prefs.edit().putBoolean(KEY_DEBUG_MODE, false).apply()
                                    debugMode = false
                                },
                            leadingContent = {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                            },
                            colors = cardRowColors,
                        ) { Text(stringResource(R.string.settings_debug_off)) }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text =
                        stringResource(
                            R.string.settings_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                    style = VectorMono,
                    color =
                        if (debugMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(top = 16.dp).clickable {
                            // 点七下开启调试模式；已开启后再点不再有作用（避免误关）
                            if (debugMode) return@clickable
                            versionTaps++
                            if (!debugMode && versionTaps >= DEBUG_TAPS) {
                                // commit() 而不是 apply()：这是用户刚做的选择，
                                // 紧接着进程被系统杀掉也不能丢
                                prefs.edit().putBoolean(KEY_DEBUG_MODE, true).commit()
                                debugMode = true
                                // 计数用完即归零：既不会残留成负数，再点也不会重复触发
                                versionTaps = 0
                            }
                        },
                )
                // 进度就地提示，同样不弹 Toast
                if (debugMode) {
                    Text(
                        text = debugOnMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else if (versionTaps > 0) {
                    Text(
                        text = tapsLeftMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 设置页里的一个日志共享单选项（与首次引导页同一组文案）。 */
@Composable
private fun LogSharingOption(
    mode: LogSharing,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        cn.nanoturtle.rootmys9280.manager.ui.screens.onboarding.logSharingLabel(mode)
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    stringResource(
                        cn.nanoturtle.rootmys9280.manager.ui.screens.onboarding
                            .logSharingDescription(mode)
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * 大小圆角分组的行容器：组内第一行上大圆角、最后一行下大圆角、中间行上下收小。
 * 每行独立 Surface（surfaceContainer 底色），行间由调用方 spacing 分隔，不粘连。
 */
@Composable
private fun GroupedRow(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    val shape =
        RoundedCornerShape(
            topStart = if (index == 0) 20.dp else 4.dp,
            topEnd = if (index == 0) 20.dp else 4.dp,
            bottomStart = if (index == count - 1) 20.dp else 4.dp,
            bottomEnd = if (index == count - 1) 20.dp else 4.dp,
        )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        content()
    }
}
