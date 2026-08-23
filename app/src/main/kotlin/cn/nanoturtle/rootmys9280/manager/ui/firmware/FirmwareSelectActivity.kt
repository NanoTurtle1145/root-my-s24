package cn.nanoturtle.rootmys9280.manager.ui.firmware

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.rootmy.RootViewModel
import cn.nanoturtle.rootmys9280.manager.ui.theme.LocalizedContent
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorTheme

/**
 * 系统版本选择 Activity（独立页面）：
 * 从 RootFlow 的版本卡片进入，列出全部固件范围单选载荷。
 *
 * 共享 [ServiceLocator.rootViewModel]（进程级单例），选中即持久化并写入
 * Compose MutableState —— 返回主界面后 RootFlow 自动重绘，无需手动刷新。
 */
class FirmwareSelectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocalizedContent {
                VectorTheme {
                    FirmwareSelectContent(
                        vm = ServiceLocator.rootViewModel,
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirmwareSelectContent(
    vm: RootViewModel,
    onBack: () -> Unit,
) {
    val firmwareVersion by vm.firmwareVersionState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rootflow_firmware_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Text(
                    text = stringResource(R.string.rootflow_firmware_hint, firmwareVersion.assetName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // 按地区分组展示：国行 / 港版台版
            RootViewModel.Region.entries.forEach { region ->
                val versions = RootViewModel.FirmwareVersion.entries.filter { it.region == region }
                if (versions.isEmpty()) return@forEach
                item {
                    Text(
                        text = region.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            versions.forEach { version ->
                                val selected = firmwareVersion == version
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = version.enabled) {
                                            vm.firmwareVersion = version
                                            onBack()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            if (version.enabled) {
                                                vm.firmwareVersion = version
                                                onBack()
                                            }
                                        },
                                        enabled = version.enabled,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        // 系统版本主标题
                                        Text(
                                            text = version.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        // 适配机型
                                        Text(
                                            text = stringResource(
                                                R.string.rootflow_firmware_device,
                                                version.device,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        // 适配系统范围
                                        Text(
                                            text = stringResource(
                                                R.string.rootflow_firmware_range,
                                                version.range,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = version.assetName.ifEmpty {
                                            stringResource(R.string.rootflow_firmware_pending)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
