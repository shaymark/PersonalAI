package com.personal.personalai.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    codeBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    codeTextColor: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val segments = remember(text) {
        val cleaned = convertLatexToPlainText(text)
        parseMarkdownSegments(cleaned)
    }

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Paragraph -> {
                    if (segment.content.isNotBlank()) {
                        val annotated = remember(segment.content, color) {
                            parseInlineMarkdown(segment.content, color, linkColor)
                        }
                        Text(
                            text = annotated,
                            style = style,
                            color = color,
                        )
                    }
                }

                is MarkdownSegment.Heading -> {
                    val baseFontSize = if (style.fontSize.isSp) style.fontSize else 14.sp
                    val headingStyle = when (segment.level) {
                        1 -> style.copy(
                            fontSize = baseFontSize * 1.5f,
                            fontWeight = FontWeight.Bold,
                        )
                        2 -> style.copy(
                            fontSize = baseFontSize * 1.3f,
                            fontWeight = FontWeight.Bold,
                        )
                        3 -> style.copy(
                            fontSize = baseFontSize * 1.15f,
                            fontWeight = FontWeight.SemiBold,
                        )
                        else -> style.copy(
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val annotated = remember(segment.content, color) {
                        parseInlineMarkdown(segment.content, color, linkColor)
                    }
                    Text(
                        text = annotated,
                        style = headingStyle,
                        color = color,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }

                is MarkdownSegment.ListItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = segment.bullet,
                            style = style,
                            color = color,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        val annotated = remember(segment.content, color) {
                            parseInlineMarkdown(segment.content, color, linkColor)
                        }
                        Text(
                            text = annotated,
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MarkdownSegment.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = color.copy(alpha = 0.3f),
                    )
                }

                is MarkdownSegment.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = codeBackgroundColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = segment.code.trimEnd(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                ),
                                color = codeTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Data model ───────────────────────────────────────────────────────────────

internal sealed interface MarkdownSegment {
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
    data class Heading(val level: Int, val content: String) : MarkdownSegment
    data class ListItem(val bullet: String, val content: String) : MarkdownSegment
    data object Divider : MarkdownSegment
    data class Paragraph(val content: String) : MarkdownSegment
}

// ── LaTeX to plain text ──────────────────────────────────────────────────────

private val SUPERSCRIPT_MAP = mapOf(
    '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3',
    '4' to '\u2074', '5' to '\u2075', '6' to '\u2076', '7' to '\u2077',
    '8' to '\u2078', '9' to '\u2079', '+' to '\u207A', '-' to '\u207B',
    'n' to '\u207F', 'i' to '\u2071',
)

private val SUBSCRIPT_MAP = mapOf(
    '0' to '\u2080', '1' to '\u2081', '2' to '\u2082', '3' to '\u2083',
    '4' to '\u2084', '5' to '\u2085', '6' to '\u2086', '7' to '\u2087',
    '8' to '\u2088', '9' to '\u2089', '+' to '\u208A', '-' to '\u208B',
)

private val LATEX_SYMBOLS = listOf(
    "\\times" to "\u00D7",
    "\\div" to "\u00F7",
    "\\approx" to "\u2248",
    "\\neq" to "\u2260",
    "\\leq" to "\u2264",
    "\\geq" to "\u2265",
    "\\pm" to "\u00B1",
    "\\cdot" to "\u00B7",
    "\\sum" to "\u03A3",
    "\\prod" to "\u03A0",
    "\\int" to "\u222B",
    "\\infty" to "\u221E",
    "\\alpha" to "\u03B1",
    "\\beta" to "\u03B2",
    "\\gamma" to "\u03B3",
    "\\delta" to "\u03B4",
    "\\theta" to "\u03B8",
    "\\lambda" to "\u03BB",
    "\\sigma" to "\u03C3",
    "\\omega" to "\u03C9",
    "\\pi" to "\u03C0",
    "\\mu" to "\u03BC",
    "\\phi" to "\u03C6",
    "\\epsilon" to "\u03B5",
    "\\quad" to " ",
    "\\;" to " ",
    "\\," to " ",
    "\\!" to "",
    "\\left" to "",
    "\\right" to "",
)

internal fun convertLatexToPlainText(text: String): String {
    var result = text

    // Strip math delimiters \( ... \) and \[ ... \]
    result = result.replace(Regex("""\\\((.*?)\\\)""")) { it.groupValues[1].trim() }
    result = result.replace(Regex("""\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1].trim() }

    // \text{...} → content
    result = result.replace(Regex("""\\text\{([^}]*)\}""")) { it.groupValues[1] }

    // \textbf{...} → content
    result = result.replace(Regex("""\\textbf\{([^}]*)\}""")) { it.groupValues[1] }

    // \frac{a}{b} → a/b
    result = result.replace(Regex("""\\frac\{([^}]*)\}\{([^}]*)\}""")) {
        "${it.groupValues[1]}/${it.groupValues[2]}"
    }

    // \sqrt{x} → √x
    result = result.replace(Regex("""\\sqrt\{([^}]*)\}""")) { "\u221A${it.groupValues[1]}" }

    // Replace known symbols (longest first to avoid partial matches)
    for ((latex, unicode) in LATEX_SYMBOLS) {
        result = result.replace(latex, unicode)
    }

    // ^{...} → superscript unicode
    result = result.replace(Regex("""\^\{([^}]*)\}""")) { match ->
        match.groupValues[1].map { SUPERSCRIPT_MAP[it] ?: it }.joinToString("")
    }
    // ^single_char → superscript unicode
    result = result.replace(Regex("""\^([0-9n])""")) { match ->
        (SUPERSCRIPT_MAP[match.groupValues[1][0]] ?: match.groupValues[1][0]).toString()
    }

    // _{...} → subscript unicode
    result = result.replace(Regex("""_\{([^}]*)\}""")) { match ->
        match.groupValues[1].map { SUBSCRIPT_MAP[it] ?: it }.joinToString("")
    }
    // _single_char → subscript unicode
    result = result.replace(Regex("""_([0-9])""")) { match ->
        (SUBSCRIPT_MAP[match.groupValues[1][0]] ?: match.groupValues[1][0]).toString()
    }

    // Strip remaining \{  \}
    result = result.replace("\\{", "{").replace("\\}", "}")

    return result
}

// ── Markdown parsing ─────────────────────────────────────────────────────────

private val CODE_BLOCK_REGEX = Regex("""```(\w*)\n([\s\S]*?)```""")
private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
private val UNORDERED_LIST_REGEX = Regex("""^[-*]\s+(.+)$""")
private val ORDERED_LIST_REGEX = Regex("""^(\d+)\.\s+(.+)$""")
private val DIVIDER_REGEX = Regex("""^---+$""")
private val DIVIDER_ASTERISK_REGEX = Regex("""^\*\*\*+$""")

internal fun parseMarkdownSegments(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    var lastIndex = 0

    // First pass: split on code blocks
    val rawParts = mutableListOf<Any>() // String or MarkdownSegment.CodeBlock
    CODE_BLOCK_REGEX.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            rawParts.add(text.substring(lastIndex, match.range.first))
        }
        rawParts.add(
            MarkdownSegment.CodeBlock(
                language = match.groupValues[1],
                code = match.groupValues[2],
            )
        )
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        rawParts.add(text.substring(lastIndex))
    }
    if (rawParts.isEmpty()) {
        rawParts.add(text)
    }

    // Second pass: classify text blocks into line-level segments
    rawParts.forEach { part ->
        when (part) {
            is MarkdownSegment.CodeBlock -> segments.add(part)
            is String -> segments.addAll(parseTextBlock(part))
        }
    }

    return segments
}

private fun parseTextBlock(rawText: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val content = paragraphBuffer.toString().trim()
        if (content.isNotEmpty()) {
            segments.add(MarkdownSegment.Paragraph(content))
        }
        paragraphBuffer.clear()
    }

    rawText.lines().forEach { line ->
        val trimmed = line.trimEnd()

        when {
            // Divider: --- or ***
            DIVIDER_REGEX.matches(trimmed) || DIVIDER_ASTERISK_REGEX.matches(trimmed) -> {
                flushParagraph()
                segments.add(MarkdownSegment.Divider)
            }
            // Heading: # ... to ###### ...
            HEADING_REGEX.matches(trimmed) -> {
                flushParagraph()
                val match = HEADING_REGEX.matchEntire(trimmed)!!
                segments.add(
                    MarkdownSegment.Heading(
                        level = match.groupValues[1].length,
                        content = match.groupValues[2],
                    )
                )
            }
            // Unordered list: - item or * item
            UNORDERED_LIST_REGEX.matches(trimmed) -> {
                flushParagraph()
                val match = UNORDERED_LIST_REGEX.matchEntire(trimmed)!!
                segments.add(
                    MarkdownSegment.ListItem(bullet = "•", content = match.groupValues[1])
                )
            }
            // Ordered list: 1. item
            ORDERED_LIST_REGEX.matches(trimmed) -> {
                flushParagraph()
                val match = ORDERED_LIST_REGEX.matchEntire(trimmed)!!
                segments.add(
                    MarkdownSegment.ListItem(
                        bullet = "${match.groupValues[1]}.",
                        content = match.groupValues[2],
                    )
                )
            }
            // Blank line ends a paragraph
            trimmed.isEmpty() -> {
                flushParagraph()
            }
            // Regular text — accumulate into paragraph
            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append('\n')
                paragraphBuffer.append(trimmed)
            }
        }
    }

    flushParagraph()
    return segments
}

// ── Inline markdown ──────────────────────────────────────────────────────────

internal fun parseInlineMarkdown(text: String, defaultColor: Color, linkColor: Color = Color.Unspecified): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Link: [text](url) → clickable link that opens URL
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket != -1
                        && closeBracket + 1 < text.length
                        && text[closeBracket + 1] == '('
                    ) {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParen)
                            val linkStyle = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                            withLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(style = linkStyle),
                                )
                            ) {
                                append(linkText)
                            }
                            i = closeParen + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline code: `...`
                text[i] == '`' && !text.regionAt(i, "```") -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = defaultColor.copy(alpha = 0.1f),
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Bold: **...**
                text.regionAt(i, "**") -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *...*  (single asterisk, not preceded by *)
                text[i] == '*' && (i == 0 || text[i - 1] != '*') && !text.regionAt(i, "**") -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && !text.regionAt(end, "**")) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

private fun String.regionAt(index: Int, prefix: String): Boolean {
    return startsWith(prefix, index)
}
