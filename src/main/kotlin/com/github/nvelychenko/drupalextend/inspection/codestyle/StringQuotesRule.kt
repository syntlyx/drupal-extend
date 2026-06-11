package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

internal object StringQuotesRule : DrupalCodeStyleRule {
    override val id: String = "string_quotes"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleStringQuotesEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val literal = element as? StringLiteralExpression ?: return

        // Fast bail-outs: single-quoted, heredoc/nowdoc, or interpolated.
        if (literal.isSingleQuote) return
        if (literal.isHeredoc) return

        // The raw text includes the surrounding delimiters, e.g. `"hello"`.
        val raw = literal.text
        if (raw.isEmpty() || raw[0] != '"') return

        // Interpolated strings have composite child PSI elements (variable references, etc.).
        // `PsiElement.children` returns only non-leaf (composite) children; a plain string has none.
        if (literal.children.isNotEmpty()) return

        // Scan the content between the outer quotes.  Bail if any escape sequence
        // requires double-quote context (i.e. is not `\\` or `\"`).
        val content = raw.substring(1, if (raw.length >= 2 && raw.last() == '"') raw.length - 1 else raw.length)
        if (!isConvertibleContent(content)) return

        context.register(
            DrupalCodeStyleProblem(
                element = literal,
                message = "Prefer single quotes for strings without interpolation or escapes",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                fixes = listOf(convertToSingleQuoteFix()),
            )
        )
    }

    private fun convertToSingleQuoteFix(): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Convert to single-quoted string") { _, descriptor ->
            (descriptor.psiElement as? StringLiteralExpression)
                ?.toSingleQuoteEdit()
                ?.let { listOf(it) }
                ?: emptyList()
        }
}

/**
 * Returns true when the raw content (between the outer `"` delimiters) contains
 * only plain characters, `\\`, or `\"` — and therefore can be represented as a
 * single-quoted string without semantic change.
 *
 * Any escape sequence that only works in double-quoted context (`\n`, `\t`, `\r`,
 * `\v`, `\e`, `\f`, `\0`–`\7`, `\x`, `\u`, `\$`) causes this to return false.
 */
private fun isConvertibleContent(content: String): Boolean {
    var i = 0
    while (i < content.length) {
        val c = content[i]
        if (c == '\\' && i + 1 < content.length) {
            when (content[i + 1]) {
                '\\', '"' -> {
                    // Convertible: `\\` stays `\\`; `\"` becomes `"`
                    i += 2
                    continue
                }
                else -> {
                    // Any other escape sequence requires double quotes.
                    return false
                }
            }
        }
        i++
    }
    return true
}

/**
 * Builds a [TextEdit] that rewrites the double-quoted literal to a single-quoted
 * one.  Conversion rules:
 *   `\"` → `"`       (backslash before double-quote is no longer needed)
 *   `'`  → `\'`      (single-quote inside single-quoted string must be escaped)
 *   `\\` → `\\`      (unchanged)
 *
 * Returns null when the element is not a convertible double-quoted string (guards
 * against stale descriptors / idempotency — if already single-quoted the function
 * returns null and the fix produces no edits).
 */
internal fun StringLiteralExpression.toSingleQuoteEdit(): TextEdit? {
    if (isSingleQuote) return null
    if (isHeredoc) return null
    if (children.isNotEmpty()) return null

    val raw = text
    if (raw.isEmpty() || raw[0] != '"') return null

    val content = raw.substring(1, if (raw.length >= 2 && raw.last() == '"') raw.length - 1 else raw.length)
    if (!isConvertibleContent(content)) return null

    val sb = StringBuilder(raw.length)
    sb.append('\'')

    var i = 0
    while (i < content.length) {
        val c = content[i]
        if (c == '\\' && i + 1 < content.length) {
            when (content[i + 1]) {
                '"' -> {
                    // `\"` → `"`
                    sb.append('"')
                    i += 2
                    continue
                }
                '\\' -> {
                    // `\\` → `\\`
                    sb.append('\\')
                    sb.append('\\')
                    i += 2
                    continue
                }
            }
        }
        if (c == '\'') {
            sb.append('\\')
            sb.append('\'')
        } else {
            sb.append(c)
        }
        i++
    }

    sb.append('\'')

    val replacement = sb.toString()
    // Idempotency guard: if the replacement equals the current text, emit nothing.
    if (replacement == raw) return null

    return TextEdit(textRange.startOffset, textRange.endOffset, replacement)
}
