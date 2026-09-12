package cn.nanoturtle.rootmys9280.manager.ui.screens.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.nanoturtle.rootmys9280.manager.R
import cn.nanoturtle.rootmys9280.manager.di.ServiceLocator
import cn.nanoturtle.rootmys9280.manager.rootmy.LogSharing
import cn.nanoturtle.rootmys9280.manager.rootmy.OnboardingPrefs
import cn.nanoturtle.rootmys9280.manager.rootmy.RootViewModel
import cn.nanoturtle.rootmys9280.manager.ui.theme.VectorMono
import kotlinx.coroutines.launch

/**
 * 问题反馈。
 *
 * 设计要点：**把关键点选项化**。用户描述问题时很难说清「哪个固件、哪一步失败、什么授权方式」，
 * 这些恰恰是定位所需；所以问题类型做成单选，环境信息自动采集并原样展示（用户能看到自己提交了什么），
 * 日志默认附带但可取消。
 */
@Composable
fun FeedbackScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val vm = ServiceLocator.rootViewModel
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf<RootViewModel.FeedbackKind?>(null) }
    var note by remember { mutableStateOf("") }
    // 默认勾选，但若用户在引导里选过「不提供」，就不再默认帮他勾上
    var attachLog by remember {
        mutableStateOf(OnboardingPrefs.logSharing(context) != LogSharing.NEVER)
    }
    var result by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    val info = remember(state.logLines.size) { vm.feedbackInfo() }
    val kinds = RootViewModel.FeedbackKind.entries

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.donate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.feedback_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // 1) 问题类型：单选，覆盖实际收到的问题
            item { FeedbackSectionLabel(stringResource(R.string.feedback_kind_label)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    kinds.forEach { k ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = kind == k, onClick = { kind = k }),
                            shape = RoundedCornerShape(14.dp),
                            color =
                                if (kind == k) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = kind == k, onClick = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(kindLabel(k)),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }

            // 2) 补充说明：可留空，结构化信息已经够定位
            item { FeedbackSectionLabel(stringResource(R.string.feedback_note_label)) }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.feedback_note_hint)) },
                    minLines = 3,
                    maxLines = 6,
                )
            }

            // 3) 自动采集的环境信息：原样展示，用户知道提交了什么
            item { FeedbackSectionLabel(stringResource(R.string.feedback_info_label)) }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        info.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = k,
                                    style = VectorMono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(text = v, style = VectorMono)
                            }
                        }
                    }
                }
            }

            // 4) 是否附带日志
            item {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = attachLog, onClick = { attachLog = !attachLog })
                                .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.feedback_attach_log),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.feedback_attach_log_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = attachLog, onCheckedChange = { attachLog = it })
                    }
                }
            }

            // 5) 提交 + 就地结果
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val selected = kind ?: return@Button
                        sending = true
                        result = null
                        scope.launch {
                            result = vm.submitFeedback(selected, note, attachLog)
                            sending = false
                        }
                    },
                    enabled = kind != null && !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.feedback_submit))
                }
            }
            result?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** 分段小标题。 */
@Composable
private fun FeedbackSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/** 问题类型 → 文案资源。 */
private fun kindLabel(kind: RootViewModel.FeedbackKind): Int =
    when (kind) {
        RootViewModel.FeedbackKind.NEVER_ROOT -> R.string.feedback_kind_never_root
        RootViewModel.FeedbackKind.STUCK -> R.string.feedback_kind_stuck
        RootViewModel.FeedbackKind.AFTER_ROOT -> R.string.feedback_kind_after_root
        RootViewModel.FeedbackKind.UI -> R.string.feedback_kind_ui
        RootViewModel.FeedbackKind.OTHER -> R.string.feedback_kind_other
    }
