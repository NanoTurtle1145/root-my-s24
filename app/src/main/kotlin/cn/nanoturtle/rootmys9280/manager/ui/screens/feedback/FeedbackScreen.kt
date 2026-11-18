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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cn.nanoturtle.rootmys9280.manager.rootmy.LogUploader
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
    var lastToken by remember { mutableStateOf<String?>(null) }
    val savedTokens = remember { vm.savedFeedbackTokens }

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
                            shape = MaterialTheme.shapes.small,
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
                    shape = MaterialTheme.shapes.medium,
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
                    shape = MaterialTheme.shapes.medium,
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
                            val r = vm.submitFeedback(selected, note, attachLog)
                            result = r.message
                            lastToken = r.token
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
            // 提交成功后：提示用户可在「我的反馈」里查看开发者回复
            if (lastToken != null) {
                item {
                    Text(
                        text = stringResource(R.string.feedback_token_saved_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            // 6) 我的反馈：凭本地保存的凭据查开发者回复
            if (savedTokens.isNotEmpty()) {
                item { FeedbackSectionLabel(stringResource(R.string.feedback_mine_label)) }
                items(savedTokens) { token ->
                    FeedbackReplyCard(token = token, vm = vm)
                }
            }
        }
    }
}

/**
 * 单条反馈卡：进入页面即自动拉取一次，展示「问题类型 / 提交时间 / 回复状态」。
 *
 * 早先版本要用户逐条点「展开」才发请求，且只显示 token 前 8 位、拿不到类型与时间，
 * 多条反馈时根本分不清哪张是哪条；再加上主线程发起网络请求被静默吞掉，
 * 实际效果就是「永远没有回复」。这里一并修掉：
 *  - 自动查询（[LaunchedEffect]），不必手点；
 *  - 四态区分（已回复 / 待回复 / 记录不存在 / 查询失败），失败可重试；
 *  - 展示服务端本就返回、此前被丢弃的 kind / note / received_at / replied_at。
 */
@Composable
private fun FeedbackReplyCard(token: String, vm: RootViewModel) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<LogUploader.FeedbackReply?>(null) }
    var loading by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            data = vm.fetchFeedbackReply(token)
            loading = false
        }
    }

    LaunchedEffect(token) {
        if (data == null) load()
    }

    val detail = data
    val statusText =
        when (detail?.status) {
            LogUploader.FeedbackReply.Status.REPLIED -> stringResource(R.string.feedback_mine_replied)
            LogUploader.FeedbackReply.Status.PENDING -> stringResource(R.string.feedback_mine_no_reply)
            LogUploader.FeedbackReply.Status.NOT_FOUND -> stringResource(R.string.feedback_mine_gone)
            LogUploader.FeedbackReply.Status.ERROR -> stringResource(R.string.feedback_mine_failed)
            null -> stringResource(R.string.feedback_mine_loading)
        }
    val statusColor =
        when (detail?.status) {
            LogUploader.FeedbackReply.Status.REPLIED -> MaterialTheme.colorScheme.primary
            LogUploader.FeedbackReply.Status.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.feedback_mine_item, token.take(8)),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
                TextButton(onClick = { load() }, enabled = !loading) {
                    Text(stringResource(R.string.feedback_mine_refresh))
                }
            }
            if (detail != null && detail.status != LogUploader.FeedbackReply.Status.NOT_FOUND) {
                val meta =
                    listOf(detail.kind.let { feedbackKindLabel(it) }, detail.receivedAt)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detail.note.isNotBlank()) {
                    val long = detail.note.length > 80
                    Text(
                        text = if (opened || !long) detail.note else detail.note.take(80) + "…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!opened && long) {
                        TextButton(onClick = { opened = true }) {
                            Text(stringResource(R.string.feedback_mine_expand))
                        }
                    }
                }
                when (detail.status) {
                    LogUploader.FeedbackReply.Status.REPLIED -> {
                        Text(
                            text =
                                if (detail.repliedAt.isNotBlank())
                                    stringResource(R.string.feedback_mine_replied_at, detail.repliedAt)
                                else stringResource(R.string.feedback_mine_replied),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = detail.reply,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    LogUploader.FeedbackReply.Status.ERROR -> {
                        Text(
                            text = stringResource(R.string.feedback_mine_error, detail.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}

/** 服务端返回的 kind 字符串 → 本地化文案；未知值原样显示（不丢信息）。 */
@Composable
private fun feedbackKindLabel(kind: String): String {
    val id =
        when (kind.uppercase()) {
            "NEVER_ROOT" -> R.string.feedback_kind_never_root
            "STUCK" -> R.string.feedback_kind_stuck
            "AFTER_ROOT" -> R.string.feedback_kind_after_root
            "UI" -> R.string.feedback_kind_ui
            "OTHER" -> R.string.feedback_kind_other
            else -> 0
        }
    return if (id != 0) stringResource(id) else kind
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
