package cn.nanoturtle.rootmys9280.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * 一个很小的 Markdown 渲染器 —— 只覆盖项目里实际用到的语法。
 *
 * 为什么不引第三方库：`privacy.md` 这类文档只用到标题、列表、表格、引用和行内样式，
 * 引入 compose-markdown 之类的依赖会多出一串传递依赖，还得自己处理 16 种语言的排版；
 * 这里 200 行就能稳定渲染，出了问题也好改。
 *
 * 支持：
 * - 标题 `#` ~ `###`（更深的按 `###` 处理）
 * - 段落 / 无序列表 `- ` / 有序列表 `1. ` / 引用 `> ` / 分割线 `---` / 围栏代码
 * - 表格（`| a | b |` + 分隔行）
 * - 行内：`**粗体**`、`*斜体*`、`` `代码` ``、`[文字](链接)`
 *
 * 链接点击通过 [MarkdownBlock.onLink] 交给调用方（App 内打开 WebView 或跳浏览器）。
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val bulletRe = Regex("^[-*+]\\s+(.*)$")
private val numberedRe = Regex("^\\d+[.)]\\s+(.*)$")
private val tableSepRe = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?$")
private val hrRe = Regex("^(-{3,}|\\*{3,}|_{3,})$")

/** 把 Markdown 文本解析成块列表。 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = mutableListOf<MarkdownBlock>()
    var i = 0

    fun flushParagraph(buffer: MutableList<String>) {
        if (buffer.isEmpty()) return
        out += MarkdownBlock.Paragraph(buffer.joinToString(" ").trim())
        buffer.clear()
    }

    val paragraph = mutableListOf<String>()
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 围栏代码块
        if (trimmed.startsWith("```")) {
            flushParagraph(paragraph)
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code += lines[i]
                i++
            }
            i++ // 跳过结束围栏
            out += MarkdownBlock.Code(code.joinToString("\n"))
            continue
        }

        // 表格：当前行含 |，下一行是分隔行
        if (trimmed.startsWith("|") && i + 1 < lines.size && tableSepRe.matches(lines[i + 1].trim())) {
            flushParagraph(paragraph)
            val header = splitTableRow(trimmed)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows += splitTableRow(lines[i].trim())
                i++
            }
            out += MarkdownBlock.Table(header, rows)
            continue
        }

        when {
            trimmed.isEmpty() -> {
                flushParagraph(paragraph)
                i++
            }
            hrRe.matches(trimmed) -> {
                flushParagraph(paragraph)
                out += MarkdownBlock.Divider
                i++
            }
            headingRe.matches(trimmed) -> {
                flushParagraph(paragraph)
                val m = headingRe.find(trimmed)!!
                out += MarkdownBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                i++
            }
            trimmed.startsWith(">") -> {
                flushParagraph(paragraph)
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                out += MarkdownBlock.Quote(quote.joinToString(" "))
            }
            bulletRe.matches(trimmed) -> {
                flushParagraph(paragraph)
                val items = mutableListOf<String>()
                while (i < lines.size && bulletRe.matches(lines[i].trim())) {
                    items += bulletRe.find(lines[i].trim())!!.groupValues[1]
                    i++
                }
                out += MarkdownBlock.BulletList(items)
            }
            numberedRe.matches(trimmed) -> {
                flushParagraph(paragraph)
                val items = mutableListOf<String>()
                while (i < lines.size && numberedRe.matches(lines[i].trim())) {
                    items += numberedRe.find(lines[i].trim())!!.groupValues[1]
                    i++
                }
                out += MarkdownBlock.NumberedList(items)
            }
            else -> {
                paragraph += trimmed
                i++
            }
        }
    }
    flushParagraph(paragraph)
    return out
}

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

// ---- 行内样式 ----

private data class InlineSpan(val start: Int, val end: Int, val url: String)

/** 生成带行内样式的 AnnotatedString，并返回链接区间。 */
private fun buildInlineText(
    text: String,
    linkColor: Color,
    codeColor: Color,
    codeBackground: Color,
): Pair<AnnotatedString, List<InlineSpan>> {
    val spans = mutableListOf<InlineSpan>()
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") || rest.startsWith("__") -> {
                val marker = rest.substring(0, 2)
                val close = rest.indexOf(marker, 2)
                if (close <= 2) { builder.append(rest.first()); i++ ; continue }
                val inner = rest.substring(2, close)
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                builder.append(inner)
                builder.pop()
                i += close + 2
            }
            rest.startsWith("`") -> {
                val close = rest.indexOf('`', 1)
                if (close <= 1) { builder.append('`'); i++; continue }
                val inner = rest.substring(1, close)
                builder.pushStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor)
                )
                builder.append(inner)
                builder.pop()
                i += close + 1
            }
            rest.startsWith("*") || rest.startsWith("_") -> {
                val marker = rest[0]
                val close = rest.indexOf(marker, 1)
                if (close <= 1) { builder.append(marker); i++; continue }
                val inner = rest.substring(1, close)
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(inner)
                builder.pop()
                i += close + 1
            }
            rest.startsWith("[") -> {
                val closeBracket = rest.indexOf(']')
                val openParen = if (closeBracket > 0) rest.indexOf('(', closeBracket) else -1
                val closeParen = if (openParen > 0) rest.indexOf(')', openParen) else -1
                if (closeBracket > 0 && openParen == closeBracket + 1 && closeParen > openParen) {
                    val label = rest.substring(1, closeBracket)
                    val url = rest.substring(openParen + 1, closeParen)
                    val start = builder.length
                    builder.pushStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    )
                    builder.append(label)
                    builder.pop()
                    spans += InlineSpan(start, builder.length, url)
                    i += closeParen + 1
                } else {
                    builder.append('[')
                    i++
                }
            }
            else -> {
                builder.append(rest.first())
                i++
            }
        }
    }
    return builder.toAnnotatedString() to spans
}

/** 行内富文本：可点击链接，其余就是普通 Text。 */
@Composable
fun InlineMarkdownText(
    text: String,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val built = remember(text, linkColor, codeColor, codeBg) {
        buildInlineText(text, linkColor, codeColor, codeBg)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = built.first,
        style = style.copy(color = color),
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(built.second) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                built.second.firstOrNull { offset in it.start until it.end }?.let { onLink(it.url) }
            }
        },
    )
}

/** 渲染一个块列表（放在 LazyColumn 里用 [item] 逐个调用）。 */
@Composable
fun MarkdownBlockView(block: MarkdownBlock, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Spacer(Modifier.height(if (block.level <= 2) 12.dp else 8.dp))
            InlineMarkdownText(
                text = block.text,
                onLink = onLink,
                style = style.copy(fontWeight = FontWeight.Bold),
                modifier = modifier.fillMaxWidth(),
            )
        }
        is MarkdownBlock.Paragraph ->
            InlineMarkdownText(block.text, onLink, modifier.fillMaxWidth())
        is MarkdownBlock.BulletList ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                        InlineMarkdownText(item, onLink, modifier = Modifier.weight(1f))
                    }
                }
            }
        is MarkdownBlock.NumberedList ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${index + 1}. ", style = MaterialTheme.typography.bodyMedium)
                        InlineMarkdownText(item, onLink, modifier = Modifier.weight(1f))
                    }
                }
            }
        is MarkdownBlock.Quote -> {
            Row(modifier.fillMaxWidth()) {
                Spacer(Modifier.width(3.dp).height(0.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    InlineMarkdownText(
                        text = block.text,
                        onLink = onLink,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is MarkdownBlock.Code -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
            )
        }
        is MarkdownBlock.Table -> {
            Column(
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            ) {
                TableRow(block.header, onLink, header = true)
                block.rows.forEachIndexed { index, row ->
                    if (index % 2 == 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    TableRow(row, onLink, header = false)
                }
            }
        }
        MarkdownBlock.Divider -> {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, onLink: (String) -> Unit, header: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (header) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        cells.forEachIndexed { index, cell ->
            InlineMarkdownText(
                text = cell,
                onLink = onLink,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
                ),
                modifier = Modifier
                    .weight(if (cells.size <= 2) 1f else 1f)
                    .padding(end = if (index < cells.lastIndex) 6.dp else 0.dp),
            )
        }
    }
}
