package cn.nanoturtle.rootmys9280.manager.ui.firmware

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.logI
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
    // 在线载荷状态（「已实测」结论 + KSU 版本清单 + 近期成功率）；拉不到就为空，一切走内置默认
    val payload by vm.payloadStatus.collectAsStateWithLifecycle()
    // 进入页面时按 TTL 决定是否刷新（force=false：缓存新鲜就不打扰服务端）
    LaunchedEffect(Unit) { vm.refreshPayloadStatus(force = false) }
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
            // 地区筛选（横向滚动，窄屏不溢出）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filterRegion == null,
                        onClick = { filterRegion = null },
                        label = { Text(stringResource(R.string.rootflow_firmware_filter_all)) },
                    )
                }
                items(RootViewModel.Region.entries.toList()) { region ->
                    FilterChip(
                        selected = filterRegion == region,
                        onClick = { filterRegion = if (filterRegion == region) null else region },
                        label = { Text(region.label) },
                    )
                }
            }
            // 机型系列筛选（横向滚动）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filterSeries == null,
                        onClick = { filterSeries = null },
                        label = { Text(stringResource(R.string.rootflow_firmware_filter_all)) },
                    )
                }
                items(RootViewModel.Series.entries.toList()) { series ->
                    FilterChip(
                        selected = filterSeries == series,
                        onClick = { filterSeries = if (filterSeries == series) null else series },
                        label = { Text(series.label) },
                    )
                }
            }
            // KSU 驱动版本：**独立选项卡**，与上面的地区/机型筛选分开。
            //
            // 之前把它混进筛选芯片里、又在每张卡片上写一行「KSU: x」，看起来像三套机制
            // （重复的 DZH3·KSU 条目 + 筛选芯片 + 卡片行），所以这里收成一处：
            // 选项卡决定"用哪个驱动"，卡片只负责选固件。
            val ksuVersions =
                RootViewModel.FirmwareVersion.entries
                    .flatMap { vm.ksuVersionsFor(it) }
                    .distinct()
                    .sortedDescending()
            val ksuPref by vm.ksuVersionState.collectAsStateWithLifecycle()
            val ksuEffective = vm.ksuVersionEffective(firmwareVersion, ksuPref)
            if (ksuVersions.size > 1) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.rootflow_ksu_tab_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ksuVersions.forEachIndexed { index, version ->
                                SegmentedButton(
                                    selected = version == ksuEffective,
                                    onClick = { vm.setKsuVersion(version) },
                                    shape = SegmentedButtonDefaults.itemShape(index, ksuVersions.size),
                                    label = { Text(version) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.rootflow_ksu_tab_hint,
                                    ksuEffective.ifEmpty { "—" },
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LaunchedEffect(ksuVersions, ksuEffective) {
                // 自检：选项卡里有哪些版本、当前生效哪个（排查"看不到某版本"直接看这行）
                logI("ksu tab: options=$ksuVersions effective=$ksuEffective")
            }
            // 状态来源提示：改了服务端 payloads.json 就能远端改「已实测」与 KSU 清单
            Text(
                text =
                    stringResource(
                        if (payload.entries.isEmpty()) R.string.rootflow_status_bundled
                        else R.string.rootflow_status_online,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(4.dp))

            // 过滤逻辑：地区 + 机型系列 + KSU 版本 + 搜索关键词（匹配机型/系统版本/固件范围）
            // 未经测试的载荷仅在设置里启用后才显示
            val normalizedQuery = query.trim().lowercase()
            val allVersions = RootViewModel.FirmwareVersion.entries
                // 「已实测」= 服务端结论优先，其次内置值（在线状态可远端上下线）
                // 用订阅到的快照判断「已实测」：vm.isTested() 内部读的是 .value
                .filter { untestedEnabled || (payload.entries[it.name]?.tested ?: it.tested) }
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
            } else if (normalizedQuery.isEmpty() && filterRegion == null && filterSeries == null &&
                true
            ) {
                // 无搜索词、无筛选 → 按地区分组展示
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 兼容范围提示：载荷按构建定标、S24 全系通用，这条最容易被误解，
                    // 所以放在列表最上面，看条目之前先看到它
                    item {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.rootflow_series_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
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
                                vm = vm,
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
                            vm = vm,
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

/** 固件版本列表：每行 系统版本 + 适配机型 + 适配系统范围，大小圆角分组。 */
@Composable
private fun FirmwareVersionCard(
    vm: RootViewModel,
    versions: List<RootViewModel.FirmwareVersion>,
    selected: RootViewModel.FirmwareVersion,
    onSelect: (RootViewModel.FirmwareVersion) -> Unit,
) {
    // 卡片**自己订阅**所依赖的状态。
    // 父级重组时 Compose 有可能跳过参数未变的子组件，所以在这里读 vm 的 `.value`
    // 是不可靠的：会出现"服务端改了已实测标记、界面却不刷新"这类假象。
    val payloadInCard by vm.payloadStatus.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        versions.forEachIndexed { index, version ->
            val isSelected = selected == version
            val isLast = index == versions.lastIndex
            val shape =
                RoundedCornerShape(
                    topStart = if (index == 0) 20.dp else 4.dp,
                    topEnd = if (index == 0) 20.dp else 4.dp,
                    bottomStart = if (isLast) 20.dp else 4.dp,
                    bottomEnd = if (isLast) 20.dp else 4.dp,
                )
            val container =
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainer
            androidx.compose.material3.Surface(
                color = container,
                shape = shape,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = version.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (payloadInCard.entries[version.name]?.tested ?: version.tested) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        text = stringResource(R.string.rootflow_firmware_tested),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
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
                        // 服务端补充说明与近期成功率（来自真实运行日志）
                        val entry = payloadInCard.entries[version.name]
                        val note = entry?.note.orEmpty()
                        if (note.isNotEmpty()) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val stats = entry?.rateText.orEmpty()
                        if (stats.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.rootflow_firmware_stats, stats),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}