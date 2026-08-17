package cn.nanoturtle.rootmys9280

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.nanoturtle.rootmys9280.ui.theme.PREFS_NAME
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 设置页（照搬 KSU SettingsMiuix 结构）：
 * 外观/运行偏好（主题、日志模式、熄屏、自动跳转）+ 关于（检查更新、项目主页）。
 * 顶栏由 RootScreen 提供（KSU 同款）。
 */
@Composable
internal fun SettingsScreen(
    vm: RootViewModel,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var briefLog by remember { mutableStateOf(prefs.getBoolean(PREFS_BRIEF_LOG, false)) }
    var autoJump by remember { mutableStateOf(prefs.getBoolean(PREFS_AUTO_JUMP, true)) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .padding(horizontal = 12.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle("通用")
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                // 主题设置（打开 KSU 同款调色板页面）
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = "主题设置",
                    summary = accentName(cn.nanoturtle.rootmys9280.ui.theme.AppThemeState.accent),
                    startAction = {
                        PreferenceIcon(Icons.Filled.Palette)
                    },
                    onClick = onOpenTheme,
                )
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    title = "日志默认模式",
                    summary = "打开日志页时默认使用粗略模式",
                    startAction = {
                        PreferenceIcon(Icons.Filled.Info)
                    },
                    checked = briefLog,
                    onCheckedChange = {
                        briefLog = it
                        prefs.edit().putBoolean(PREFS_BRIEF_LOG, it).apply()
                    },
                )
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    title = "运行期间自动熄屏",
                    summary = "显示驱动停止，大幅降低崩溃概率",
                    startAction = {
                        PreferenceIcon(Icons.Filled.Warning)
                    },
                    checked = vm.autoScreenOff,
                    onCheckedChange = { vm.setAutoScreenOff(it) },
                )
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    title = "开始后自动跳转日志页",
                    summary = "点击“开始 Root”后自动切到日志页",
                    startAction = {
                        PreferenceIcon(Icons.AutoMirrored.Filled.List)
                    },
                    checked = autoJump,
                    onCheckedChange = {
                        autoJump = it
                        prefs.edit().putBoolean(PREFS_AUTO_JUMP, it).apply()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("关于")
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = "检查更新",
                    summary = "查看 GitHub Releases",
                    startAction = {
                        PreferenceIcon(Icons.Filled.Update)
                    },
                    onClick = { openUrl(context, "$REPO_URL/releases") },
                )
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = "项目主页",
                    summary = REPO_URL.removePrefix("https://"),
                    startAction = {
                        PreferenceIcon(Icons.Filled.Home)
                    },
                    onClick = { openUrl(context, REPO_URL) },
                )
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = "许可公示",
                    summary = "GPL-3.0 · KernelSU/LSPosed 等组件许可",
                    startAction = {
                        PreferenceIcon(Icons.Filled.CheckCircle)
                    },
                    onClick = { openUrl(context, "$REPO_URL") },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** preference 条目起始图标（KSU 同款圆角容器） */
@Composable
private fun PreferenceIcon(icon: ImageVector) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(end = 6.dp),
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(6.dp),
        )
    }
}
