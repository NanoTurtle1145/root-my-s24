package cn.nanoturtle.rootmys9280.manager.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.rootmy.LogSharing
import cn.nanoturtle.rootmys9280.manager.rootmy.OnboardingPrefs
import cn.nanoturtle.rootmys9280.manager.ui.screens.wiki.WikiContent

/**
 * 首次启动引导：必读指南 → 通知权限 → 运行日志共享方式。
 *
 * 只有第一次打开应用时出现（[OnboardingPrefs.isDone] 为 false），三件事做完就写标记不再打扰。
 * 三页的内容都有别处可改：指南在「关于」页随时能再读，日志共享在「设置 → 隐私」里能改，
 * 通知权限走系统设置——引导只负责"第一次一定看到"，不制造只能在这里做的选择。
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var logSharing by remember { mutableStateOf(OnboardingPrefs.logSharing(context)) }

    // Android 13 以下没有运行时通知权限；已授予过的也不再问，两种情况都直接跳过这一页。
    val needsNotification =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            OnboardingPrefs.markNotificationAsked(context)
            step = 2
        }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (step) {
                // 第 1 步：必读指南。读完点按钮才放行，避免用户不知道临时 root 的性质就去安装 LKM。
                0 -> {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            modifier =
                                Modifier.weight(1f).verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                        ) {
                            WikiContent()
                            Spacer(Modifier.height(16.dp))
                        }
                        OnboardingFooter(
                            primary = stringResource(R.string.onboarding_wiki_agree),
                            onPrimary = {
                                OnboardingPrefs.markWikiAccepted(context)
                                step = if (needsNotification) 1 else 2
                            },
                        )
                    }
                }
                // 第 2 步：通知权限。Root 过程中会自动熄屏，结果只能靠通知传达。
                1 -> {
                    OnboardingPage(
                        icon = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                        title = stringResource(R.string.onboarding_notif_title),
                        body = stringResource(R.string.onboarding_notif_body),
                        footer = {
                            Column(Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        OnboardingPrefs.markNotificationAsked(context)
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.onboarding_notif_grant))
                                }
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        OnboardingPrefs.markNotificationAsked(context)
                                        step = 2
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.onboarding_notif_skip))
                                }
                            }
                        },
                    )
                }
                // 第 3 步：日志共享方式。默认「每次询问」，需要用户主动选「始终」才会自动上传。
                else -> {
                    OnboardingPage(
                        icon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                        title = stringResource(R.string.onboarding_log_title),
                        body = stringResource(R.string.onboarding_log_body),
                        footer = {
                            Column(Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        OnboardingPrefs.setLogSharing(context, logSharing)
                                        OnboardingPrefs.markDone(context)
                                        onFinished()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.onboarding_finish))
                                }
                            }
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LogSharing.entries.forEach { mode ->
                                LogSharingRow(
                                    mode = mode,
                                    selected = logSharing == mode,
                                    onSelect = { logSharing = mode },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 三页共用的页面骨架：图标 + 标题 + 正文 + 底部按钮区（正文区可换成自定义内容）。 */
@Composable
private fun OnboardingPage(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    footer: @Composable () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.padding(16.dp)) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides
                            MaterialTheme.colorScheme.onPrimaryContainer
                    ) { icon() }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (content != null) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
        OnboardingFooter { footer() }
    }
}

/** 底部固定按钮区：始终贴在屏幕下方，正文再长也不会把按钮顶走。 */
@Composable
private fun OnboardingFooter(
    primary: String? = null,
    onPrimary: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (content != null) {
                content()
            } else if (primary != null && onPrimary != null) {
                Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
            }
        }
    }
}

/** 日志共享的一个单选项：标题 + 说明 + 单选圆点。 */
@Composable
private fun LogSharingRow(
    mode: LogSharing,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(logSharingLabel(mode)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(logSharingDescription(mode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 选项标题的资源 id（引导页与设置页共用，避免两处文案走样）。 */
fun logSharingLabel(mode: LogSharing): Int =
    when (mode) {
        LogSharing.ALWAYS -> R.string.log_sharing_always
        LogSharing.MANUAL -> R.string.log_sharing_manual
        LogSharing.NEVER -> R.string.log_sharing_never
    }

/** 选项说明的资源 id。 */
fun logSharingDescription(mode: LogSharing): Int =
    when (mode) {
        LogSharing.ALWAYS -> R.string.log_sharing_always_desc
        LogSharing.MANUAL -> R.string.log_sharing_manual_desc
        LogSharing.NEVER -> R.string.log_sharing_never_desc
    }
