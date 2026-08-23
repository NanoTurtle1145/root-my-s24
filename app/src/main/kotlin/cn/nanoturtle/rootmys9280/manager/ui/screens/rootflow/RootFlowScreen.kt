package cn.nanoturtle.rootmys9280.manager.ui.screens.rootflow

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.rootmy.AdbPairingFlow
import cn.nanoturtle.rootmys9280.manager.rootmy.AdbWirelessController
import cn.nanoturtle.rootmys9280.manager.rootmy.RootViewModel
import cn.nanoturtle.rootmys9280.manager.ui.components.VectorAmbienceSettings
import cn.nanoturtle.rootmys9280.ui.StatusHeader
import cn.nanoturtle.rootmys9280.ui.StatusTone
import cn.nanoturtle.rootmys9280.ui.ambience.AmbienceKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 免解锁 Root tab 页（Material3 版）：
 * 开始按钮 + 设置卡（自动熄屏/KNOX/导出日志）+ 运行日志（详细/粗略）。
 */
@Composable
fun RootFlowScreen(
    vm: RootViewModel = ServiceLocator.rootViewModel,
    onGoAbout: (() -> Unit)? = null,
    onOpenFirmwareSelect: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    RootFlowContent(
        vm = vm,
        state = state,
        firmwareVersion = vm.firmwareVersionState.collectAsStateWithLifecycle().value,
        onGoAbout = onGoAbout,
        onOpenFirmwareSelect = onOpenFirmwareSelect,
    )
}

@Composable
private fun RootFlowContent(
    vm: RootViewModel,
    state: RootViewModel.UiState,
    firmwareVersion: RootViewModel.FirmwareVersion,
    modifier: Modifier = Modifier,
    onGoAbout: (() -> Unit)? = null,
    onOpenFirmwareSelect: (() -> Unit)? = null,
) {
    var brief by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var showAppearance by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var donationMilestone by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 捐赠里程碑事件：达到 10/25/50/75/100… 次 root 成功时弹出一次
    LaunchedEffect(Unit) {
        vm.donationEvent.collect { count -> donationMilestone = count }
    }

    // 轮询检测 KSU 驱动状态：root 流程可能在页面打开后完成，
    // 单次检测会漏掉。每 2 秒刷新一次，驱动一加载就显示"已激活"。
    LaunchedEffect(Unit) {
        // 自动申请 Shizuku 权限（未授权时弹系统授权框，幂等）
        vm.ensureShizukuPermission()
        vm.refreshKsuStatus()
        vm.refreshKnox()
        while (true) {
            delay(2_000)
            vm.refreshKsuStatus()
        }
    }

    val shown = if (brief) state.logLines.filter { it.summary } else state.logLines
    LaunchedEffect(shown.size, brief) {
        if (shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }

    if (showAppearance) {
        cn.nanoturtle.rootmys9280.manager.ui.components.HomeAppearanceSheet(
            onDismiss = { showAppearance = false },
        )
    }
    if (showLanguage) {
        cn.nanoturtle.rootmys9280.ui.locale.LanguageSheet(
            controller = cn.nanoturtle.rootmys9280.manager.di.VectorLocaleController,
            onDismiss = { showLanguage = false },
        )
    }

    if (donationMilestone > 0) {
        AlertDialog(
            onDismissRequest = { donationMilestone = 0 },
            title = {
                Text(stringResource(R.string.donation_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.donation_body,
                        donationMilestone,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        donationMilestone = 0
                        onGoAbout?.invoke()
                    },
                ) {
                    Text(stringResource(R.string.donation_go_about))
                }
            },
            dismissButton = {
                TextButton(onClick = { donationMilestone = 0 }) {
                    Text(stringResource(R.string.donation_later))
                }
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize(),
    ) {
        item {
            // Vector 同款完整状态 banner：动态背景 + 品牌 + 状态徽章（呼吸动画）+ 详情行。
            // 全出血：延伸到屏幕两端，无水平内边距。
            val ambienceKey by ServiceLocator.settings.headerAmbience.collectAsStateWithLifecycle()
            // 状态：驱动已加载=已激活，运行中=检查中，否则=未激活。
            // ksuLoaded 是实际检测（/proc/modules），不依赖本次会话是否跑过流程。
            val tone: StatusTone =
                when {
                    state.busy -> StatusTone.Neutral
                    state.ksuLoaded -> StatusTone.Active
                    else -> StatusTone.Error
                }
            val statusWord =
                when {
                    state.busy -> stringResource(R.string.status_checking)
                    state.ksuLoaded -> stringResource(R.string.status_active)
                    else -> stringResource(R.string.status_inactive)
                }
            StatusHeader(
                brand = "RootMyS24",
                statusWord = statusWord,
                tone = tone,
                ambience = AmbienceKind.from(ambienceKey),
                ambienceSettings = VectorAmbienceSettings,
                detail = { contentColor ->
                    Text(
                        text = state.knoxState.ifBlank { stringResource(R.string.rootflow_detail_fallback) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                },
                appearanceLabel = stringResource(R.string.appearance_title),
                onOpenAppearance = { showAppearance = true },
                languageLabel = stringResource(R.string.language_title),
                onOpenLanguage = { showLanguage = true },
            )
        }

        item {
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    // 目标系统版本选择（入口卡片 → 独立选择 Activity）
                    Text(
                        text = stringResource(R.string.rootflow_firmware_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    // 当前选择的版本摘要，点击进入独立选择页
                    val selectedVersion = firmwareVersion
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.busy) { onOpenFirmwareSelect?.invoke() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = true,
                            onClick = { onOpenFirmwareSelect?.invoke() },
                            enabled = !state.busy,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${selectedVersion.region.label} · ${selectedVersion.label}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(
                                    R.string.rootflow_firmware_device,
                                    selectedVersion.device,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.rootflow_firmware_range,
                                    selectedVersion.range,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.rootflow_firmware_change),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.rootflow_firmware_hint, selectedVersion.assetName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Button(
                            onClick = { vm.start() },
                            enabled = !state.busy,
                        ) {
                            Text(
                                if (state.busy) {
                                    stringResource(R.string.rootflow_running)
                                } else {
                                    stringResource(R.string.rootflow_start)
                                },
                            )
                        }
                        Spacer(Modifier.padding(start = 12.dp))
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        }
                        if (state.rooted) {
                            Text("✓ ${stringResource(R.string.rootflow_done)}", color = Color(0xFF4CAF50))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.rootflow_screen_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            AuthCard(
                vm = vm,
                state = state,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
        }

        item {
            SettingsCard(
                vm = vm,
                state = state,
                exportResult = exportResult,
                onExport = {
                    scope.launch { exportResult = vm.dumpLog() }
                },
                onExportResultShown = { exportResult = null },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rootflow_log_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { brief = !brief },
                ) {
                    Text(if (brief) stringResource(R.string.rootflow_detail) else stringResource(R.string.rootflow_brief))
                }
            }
        }

        items(shown) { line ->
            Text(
                text = line.text,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (line.summary) FontWeight.Bold else FontWeight.Normal,
                color = if (line.summary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .padding(vertical = 1.dp),
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthCard(
    vm: RootViewModel,
    state: RootViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val method = vm.authMethod
    // 无线调试授权是否启用（设置页开关，默认关闭）：关闭时主页不显示任何无线调试控件
    val adbEnabled by vm.adbWirelessEnabled.collectAsStateWithLifecycle()
    val effectiveMethod = if (adbEnabled) method else RootViewModel.AuthMethod.SHIZUKU
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }
    var pairError by remember { mutableStateOf<String?>(null) }
    // Android 13+ 通知权限：拒绝后再次点击会直接进系统设置
    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val err = vm.startAdbPairingNotification(context)
            if (err != null) pairError = err
        } else {
            pairError = context.getString(R.string.notification_adb_pairing_no_permission)
        }
    }

    Card(modifier = modifier.padding(top = 8.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.rootflow_auth_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            // 授权方式选择：Shizuku / 无线调试（无线调试仅在设置页开启后显示）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rootflow_auth_shizuku),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !state.busy) {
                            vm.setAuthMethod(RootViewModel.AuthMethod.SHIZUKU)
                            pairError = null
                        },
                )
                androidx.compose.material3.RadioButton(
                    selected = effectiveMethod == RootViewModel.AuthMethod.SHIZUKU,
                    onClick = {
                        vm.setAuthMethod(RootViewModel.AuthMethod.SHIZUKU)
                        pairError = null
                    },
                    enabled = !state.busy,
                )
            }
            if (adbEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rootflow_auth_adb),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !state.busy) {
                                vm.setAuthMethod(RootViewModel.AuthMethod.ADB_WIRELESS)
                                pairError = null
                            },
                    )
                    androidx.compose.material3.RadioButton(
                        selected = effectiveMethod == RootViewModel.AuthMethod.ADB_WIRELESS,
                        onClick = {
                            vm.setAuthMethod(RootViewModel.AuthMethod.ADB_WIRELESS)
                            pairError = null
                        },
                        enabled = !state.busy,
                    )
                }
            }

            // 无线调试模式下：配对 + 连接（仅在设置页开启后显示）
            if (adbEnabled && effectiveMethod == RootViewModel.AuthMethod.ADB_WIRELESS) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val connected = AdbWirelessController.isConnected()
                Text(
                    text = if (connected) {
                        stringResource(R.string.rootflow_auth_adb_connected)
                    } else if (paired) {
                        stringResource(R.string.rootflow_auth_adb_paired)
                    } else {
                        stringResource(R.string.rootflow_auth_adb_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ---- 配对区（未连接时显示）----
                if (!connected) {
                    // 通知配对：mDNS 自动发现 + 通知输入配对码（Android 11+）
                    if (AdbPairingFlow.isAvailable()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.rootflow_auth_adb_pair_notify_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            androidx.compose.material3.Button(
                                onClick = {
                                    pairError = null
                                    // Android 13+ 先请求通知权限，授权后再启动配对
                                    val granted = Build.VERSION.SDK_INT < 33 ||
                                        NotificationManagerCompat.from(context).areNotificationsEnabled()
                                    if (granted) {
                                        val err = vm.startAdbPairingNotification(context)
                                        if (err != null) pairError = err
                                    } else {
                                        requestNotificationPermissionLauncher.launch(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                },
                                enabled = !connecting && !pairing && !AdbPairingFlow.isSearching(),
                            ) {
                                Text(stringResource(R.string.rootflow_auth_adb_pair_notify))
                            }
                        }
                        if (AdbPairingFlow.isSearching()) {
                            Text(
                                text = stringResource(R.string.rootflow_auth_adb_pairing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }

                    // 手动配对：配对码输入
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text(stringResource(R.string.rootflow_auth_adb_host)) },
                            singleLine = true,
                            enabled = !connecting && !pairing,
                            modifier = Modifier.weight(1.4f),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = pairPort,
                            onValueChange = { pairPort = it },
                            label = { Text(stringResource(R.string.rootflow_auth_adb_pair_port)) },
                            singleLine = true,
                            enabled = !connecting && !pairing,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = pairCode,
                            onValueChange = { pairCode = it },
                            label = { Text(stringResource(R.string.rootflow_auth_adb_pair_code)) },
                            singleLine = true,
                            enabled = !connecting && !pairing,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    pairing = true
                                    pairError = null
                                    val result = vm.pairAdbWireless(host, pairPort, pairCode)
                                    if (result == null) {
                                        paired = true
                                    } else {
                                        pairError = result
                                    }
                                    pairing = false
                                }
                            },
                            enabled = !connecting && !pairing,
                        ) {
                            if (pairing) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                            } else {
                                Text(stringResource(R.string.rootflow_auth_adb_pair))
                            }
                        }
                    }

                    // 连接区（IP 已在上方 + 连接端口 + 连接按钮）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.rootflow_auth_adb_host),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.6f),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text(stringResource(R.string.rootflow_auth_adb_port)) },
                            singleLine = true,
                            enabled = !connecting && !pairing,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    connecting = true
                                    pairError = null
                                    pairError = vm.connectAdbWireless(host, port)
                                    connecting = false
                                }
                            },
                            enabled = !connecting && !pairing,
                        ) {
                            if (connecting) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                            } else {
                                Text(stringResource(R.string.rootflow_auth_adb_connect))
                            }
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            vm.disconnectAdbWireless()
                            pairError = null
                        },
                        enabled = !state.busy,
                    ) {
                        Text(stringResource(R.string.rootflow_auth_adb_disconnect))
                    }
                }
                if (pairError != null) {
                    Text(
                        text = pairError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    vm: RootViewModel,
    state: RootViewModel.UiState,
    exportResult: String?,
    onExport: () -> Unit,
    onExportResultShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(exportResult) {
        if (exportResult != null) {
            delay(6_000)
            onExportResultShown()
        }
    }
    Card(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rootflow_auto_screen_off), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.rootflow_auto_screen_off_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = vm.autoScreenOff,
                    onCheckedChange = { vm.setAutoScreenOff(it) },
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rootflow_knox_status), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.knoxState.ifBlank { stringResource(R.string.rootflow_knox_loading) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rootflow_export_log), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        exportResult ?: stringResource(R.string.rootflow_export_log_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onExport,
                ) {
                    Text(stringResource(R.string.rootflow_export))
                }
            }
        }
    }
}
