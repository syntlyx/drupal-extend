package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression

internal object MultilineArrayStyleRule : DrupalCodeStyleRule {
    override val id: String = "multiline_array_style"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleMultilineArrayStyleEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val arrayExpression = element as? ArrayCreationExpression ?: return
        val document = arrayExpression.document() ?: return

        arrayExpression.multilineArrayStyleIssues(document).forEach { issue ->
            context.register(
                DrupalCodeStyleProblem(
                    element = arrayExpression,
                    message = issue.message,
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    rangeInElement = arrayExpression.relativeRange(issue.rangeStartOffset, issue.rangeEndOffset),
                    fixes = listOf(normalizeMultilineArrayStyleFix())
                )
            )
        }
    }

    private fun normalizeMultilineArrayStyleFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize multiline array style") { _, descriptor ->
            val arrayExpression = descriptor.psiElement as? ArrayCreationExpression
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = arrayExpression.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            arrayExpression.multilineArrayStyleEdits(document)
        }
    }
}

internal fun ArrayCreationExpression.multilineArrayStyleEdits(document: Document): List<TextEdit> {
    return multilineArrayStyleIssues(document)
        .map { it.edit }
        .distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
}

private fun ArrayCreationExpression.multilineArrayStyleIssues(document: Document): List<MultilineArrayStyleIssue> {
    if (!isShortSyntax) return emptyList()

    val bracketPair = shortArrayBrackets() ?: return emptyList()
    val openLine = document.getLineNumber(bracketPair.openOffset)
    val closeLine = document.getLineNumber(bracketPair.closeOffset)
    if (openLine == closeLine) return emptyList()

    // Bug 1 fix: use ALL direct value children (hash elements AND plain expressions),
    // not only hashElements, so that positional arrays like ['a', 'b'] are covered.
    val lastElement = arrayValueElements()
        .maxByOrNull { it.textRange.endOffset }
        ?: return emptyList()

    val issues = mutableListOf<MultilineArrayStyleIssue>()
    val arrayIndent = document.lineIndent(openLine)
    val lineSeparator = document.lineSeparator()
    val lastElementEndLine = document.getLineNumber(lastElement.textRange.endOffset - 1)
    val closeBracketEdit = closeBracketLineEdit(
        lastElement = lastElement,
        closeBracketOffset = bracketPair.closeOffset,
        document = document,
        arrayIndent = arrayIndent,
        lineSeparator = lineSeparator,
    )

    if (!lastElement.hasTrailingCommaBefore(bracketPair.closeOffset, document)) {
        issues += MultilineArrayStyleIssue(
            message = "Multiline arrays must have a trailing comma after the last element",
            edit = if (lastElementEndLine == closeLine && closeBracketEdit != null) {
                closeBracketEdit
            } else {
                TextEdit(lastElement.textRange.endOffset, ",")
            },
            rangeStartOffset = lastElement.textRange.startOffset,
            rangeEndOffset = lastElement.textRange.endOffset,
        )
    }

    if (closeLine == lastElementEndLine || document.lineText(closeLine).substringBefore("]") != arrayIndent) {
        val edit = closeBracketEdit ?: TextEdit(
            document.getLineStartOffset(closeLine),
            bracketPair.closeOffset,
            arrayIndent,
        )
        issues += MultilineArrayStyleIssue(
            message = "The closing bracket of a multiline array must be on a new line",
            edit = edit,
            rangeStartOffset = bracketPair.closeOffset,
            rangeEndOffset = bracketPair.closeOffset + 1,
        )
    }

    return issues
}

/**
 * Returns the direct PSI children of the array that represent actual values —
 * both [ArrayHashElement] nodes (key => value pairs) and plain expression nodes
 * (positional values like `['a', 'b']`). Bracket tokens, commas, and whitespace
 * are excluded.
 */
private fun ArrayCreationExpression.arrayValueElements(): List<PsiElement> {
    val result = mutableListOf<PsiElement>()
    var child = firstChild
    while (child != null) {
        val elementType = child.node?.elementType
        if (child !is PsiWhiteSpace
            && elementType != PhpTokenTypes.chLBRACKET
            && elementType != PhpTokenTypes.chRBRACKET
            && elementType != PhpTokenTypes.opCOMMA
        ) {
            result += child
        }
        child = child.nextSibling
    }
    return result
}

private data class MultilineArrayStyleIssue(
    val message: String,
    val edit: TextEdit,
    val rangeStartOffset: Int,
    val rangeEndOffset: Int,
)

private data class ShortArrayBrackets(
    val openOffset: Int,
    val closeOffset: Int,
)

/**
 * Derives the opening and closing bracket offsets from the [ArrayCreationExpression]
 * PSI text range rather than scanning raw text.
 *
 * For a short-syntax array `[...]`, the PSI node always starts with `[` and ends
 * with `]`, so `textRange.startOffset` and `textRange.endOffset - 1` give the
 * exact bracket positions without touching string or comment content.
 *
 * Bug 2 fix: the previous implementation used a raw-text bracket-depth scanner that
 * could be confused by `]` characters inside strings (it handled string quoting) or
 * comments (which it did NOT handle), producing a wrong closeOffset for arrays like
 * `['a', // ]\n 'b']`.
 */
private fun ArrayCreationExpression.shortArrayBrackets(): ShortArrayBrackets? {
    val open = textRange.startOffset
    val close = textRange.endOffset - 1
    if (close < open) return null
    return ShortArrayBrackets(open, close)
}

private fun closeBracketLineEdit(
    lastElement: PsiElement,
    closeBracketOffset: Int,
    document: Document,
    arrayIndent: String,
    lineSeparator: String,
): TextEdit? {
    val lastElementEndOffset = lastElement.textRange.endOffset
    if (lastElementEndOffset > closeBracketOffset) return null

    val gap = document.charsSequence.subSequence(lastElementEndOffset, closeBracketOffset).toString()
    if (gap.any { !it.isWhitespace() && it != ',' }) return null

    return TextEdit(lastElementEndOffset, closeBracketOffset, ",$lineSeparator$arrayIndent")
}

private fun PsiElement.hasTrailingCommaBefore(closeBracketOffset: Int, document: Document): Boolean {
    if (textRange.endOffset >= closeBracketOffset) return false
    return document.charsSequence
        .subSequence(textRange.endOffset, closeBracketOffset)
        .contains(',')
}

private fun Document.lineIndent(line: Int): String {
    return lineText(line).takeWhile { it == ' ' || it == '\t' }
}
