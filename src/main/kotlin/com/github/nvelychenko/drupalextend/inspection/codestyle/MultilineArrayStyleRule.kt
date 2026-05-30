package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement

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

    val bracketPair = shortArrayBrackets(document) ?: return emptyList()
    val openLine = document.getLineNumber(bracketPair.openOffset)
    val closeLine = document.getLineNumber(bracketPair.closeOffset)
    if (openLine == closeLine) return emptyList()

    val lastElement = hashElements.toList()
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

private fun ArrayCreationExpression.shortArrayBrackets(document: Document): ShortArrayBrackets? {
    val openOffset = textRange.startOffset
    if (document.charsSequence.getOrNull(openOffset) != '[') return null

    val closeOffset = document.charsSequence.matchingBracketOffset(openOffset, textRange.endOffset) ?: return null
    return ShortArrayBrackets(openOffset, closeOffset)
}

private fun closeBracketLineEdit(
    lastElement: ArrayHashElement,
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

private fun ArrayHashElement.hasTrailingCommaBefore(closeBracketOffset: Int, document: Document): Boolean {
    if (textRange.endOffset >= closeBracketOffset) return false
    return document.charsSequence
        .subSequence(textRange.endOffset, closeBracketOffset)
        .contains(',')
}

private fun CharSequence.matchingBracketOffset(openOffset: Int, endOffset: Int): Int? {
    var depth = 0
    var inSingleQuotedString = false
    var inDoubleQuotedString = false
    var escaped = false
    val end = endOffset.coerceAtMost(length)

    for (offset in openOffset until end) {
        val char = this[offset]
        when {
            escaped -> escaped = false
            char == '\\' && (inSingleQuotedString || inDoubleQuotedString) -> escaped = true
            char == '\'' && !inDoubleQuotedString -> inSingleQuotedString = !inSingleQuotedString
            char == '"' && !inSingleQuotedString -> inDoubleQuotedString = !inDoubleQuotedString
            inSingleQuotedString || inDoubleQuotedString -> Unit
            char == '[' -> depth++
            char == ']' -> {
                depth--
                if (depth == 0) return offset
            }
        }
    }

    return null
}

private fun Document.lineIndent(line: Int): String {
    return lineText(line).takeWhile { it == ' ' || it == '\t' }
}
