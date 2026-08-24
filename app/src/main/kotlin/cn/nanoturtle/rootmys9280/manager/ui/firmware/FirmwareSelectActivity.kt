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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
    val untestedEnabled by vm.untestedPayloadsEnabled.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // null = 全部地区；否则只显示该地区
    var filterRegion by remember { mutableStateOf<RootViewModel.Region?>(null) }
    // null = 全部机型系列；否则只显示该系列
    var filterSeries by remember { mutableStateOf<RootViewModel.Series?>(null) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // MD3 搜索框：全圆角药丸形
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.rootflow_firmware_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
            )
            // 地区筛选
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filterRegion == null,
                    onClick = { filterRegion = null },
                    label = { Text(stringResource(R.string.rootflow_firmware_filter_all)) },
                )
                Spacer(Modifier.width(8.dp))
                RootViewModel.Region.entries.forEach { region ->
                    FilterChip(
                        selected = filterRegion == region,
                        onClick = { filterRegion = if (filterRegion == region) null else region },
                        label = { Text(region.label) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            // 机型系列筛选
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filterSeries == null,
                    onClick = { filterSeries = null },
                    label = { Text(stringResource(R.string.rootflow_firmware_filter_all)) },
                )
                Spacer(Modifier.width(8.dp))
                RootViewModel.Series.entries.forEach { series ->
                    FilterChip(
                        selected = filterSeries == series,
                        onClick = { filterSeries = if (filterSeries == series) null else series },
                        label = { Text(series.label) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))

            // 过滤逻辑：地区 + 机型系列 + 搜索关键词（匹配机型/系统版本/固件范围）
            // 未经测试的载荷仅在设置里启用后才显示
            val normalizedQuery = query.trim().lowercase()
            val allVersions = RootViewModel.FirmwareVersion.entries
                .filter { untestedEnabled || it.tested }
                .filter { filterRegion == null || it.region == filterRegion }
                .filter { filterSeries == null || it.series == filterSeries }
                .filter { version ->
                    normalizedQuery.isEmpty() ||
                        version.label.lowercase().contains(normalizedQuery) ||
                        version.device.lowercase().contains(normalizedQuery) ||
                        version.range.lowercase().contains(normalizedQuery)
                }

            if (allVersions.isEmpty()) {
                // 无匹配结果
                Text(
                    text = stringResource(R.string.rootflow_firmware_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else if (normalizedQuery.isEmpty() && filterRegion == null && filterSeries == null) {
                // 无搜索词、无筛选 → 按地区分组展示
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RootViewModel.Region.entries.forEach { region ->
                        val versions = allVersions.filter { it.region == region }
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
                            FirmwareVersionCard(
                                versions = versions,
                                selected = firmwareVersion,
                                onSelect = {
                                    vm.firmwareVersion = it
                                    onBack()
                                },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                // 有搜索词或筛选 → 平铺展示（不再分组）
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        FirmwareVersionCard(
                            versions = allVersions,
                            selected = firmwareVersion,
                            onSelect = {
                                vm.firmwareVersion = it
                                onBack()
                            },
                        )
                    }
                    item {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** 固件版本列表卡片：每行 系统版本 + 适配机型 + 适配系统范围 */
@Composable
private fun FirmwareVersionCard(
    versions: List<RootViewModel.FirmwareVersion>,
    selected: RootViewModel.FirmwareVersion,
    onSelect: (RootViewModel.FirmwareVersion) -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            versions.forEach { version ->
                val isSelected = selected == version
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = version.enabled) { onSelect(version) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { if (version.enabled) onSelect(version) },
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
                }
            }
        }
    }
}