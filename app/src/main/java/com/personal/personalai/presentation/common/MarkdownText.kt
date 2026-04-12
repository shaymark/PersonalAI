package com.personal.personalai.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
) {
    val segments = remember(text) {
        val cleaned = convertLatexToPlainText(text)
        parseMarkdownSegments(cleaned)
    }

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.TextBlock -> {
                    if (segment.content.isNotBlank()) {
                        val annotated = remember(segment.content, color) {
                            parseInlineMarkdown(segment.content, color)
                        }
                        Text(
                            text = annotated,
                            style = style,
                            color = color,
                        )
                    }
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
    data class TextBlock(val content: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
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

internal fun parseMarkdownSegments(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    var lastIndex = 0

    CODE_BLOCK_REGEX.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            segments.add(MarkdownSegment.TextBlock(text.substring(lastIndex, match.range.first)))
        }
        segments.add(MarkdownSegment.CodeBlock(
            language = match.groupValues[1],
            code = match.groupValues[2],
        ))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        segments.add(MarkdownSegment.TextBlock(text.substring(lastIndex)))
    }

    if (segments.isEmpty()) {
        segments.add(MarkdownSegment.TextBlock(text))
    }

    return segments
}

// ── Inline markdown ──────────────────────────────────────────────────────────

internal fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
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
