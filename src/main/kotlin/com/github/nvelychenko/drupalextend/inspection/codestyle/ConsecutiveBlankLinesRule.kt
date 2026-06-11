package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile

internal object ConsecutiveBlankLinesRule : DrupalCodeStyleRule {
    override val id: String = "consecutive_blank_lines"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean =
        settings.isCodeStyleConsecutiveBlankLinesEnabled

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val file = element as? PhpFile ?: return
        val fileLength = file.textLength

        consecutiveBlankLineIssues(file, fileLength).forEach { issue ->
            context.register(
                DrupalCodeStyleProblem(
                    element = file,
                    message = "No more than one consecutive blank line is allowed",
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    rangeInElement = file.relativeRange(issue.startOffset, issue.endOffset),
                    fixes = listOf(collapseBlankLinesFix()),
                )
            )
        }
    }

    private fun collapseBlankLinesFix(): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Collapse consecutive blank lines") { _, descriptor ->
            val file = descriptor.psiElement.containingFile ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val fileLength = file.textLength
            consecutiveBlankLineEdits(file, fileLength)
        }
}

private data class ConsecutiveBlankLineIssue(
    val startOffset: Int,
    val endOffset: Int,
)

private fun consecutiveBlankLineIssues(file: PhpFile, fileLength: Int): List<ConsecutiveBlankLineIssue> {
    val issues = mutableListOf<ConsecutiveBlankLineIssue>()
    PsiTreeUtil.collectElements(file) { it is PsiWhiteSpace }.forEach { ws ->
        val issue = ws.consecutiveBlankLineIssue(fileLength) ?: return@forEach
        issues += issue
    }
    return issues
}

internal fun consecutiveBlankLineEdits(file: PsiElement, fileLength: Int): List<TextEdit> {
    return PsiTreeUtil.collectElements(file) { it is PsiWhiteSpace }
        .mapNotNull { ws -> ws.consecutiveBlankLineEdit(fileLength) }
        .distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
}

private fun PsiElement.consecutiveBlankLineIssue(fileLength: Int): ConsecutiveBlankLineIssue? {
    val edit = consecutiveBlankLineEdit(fileLength) ?: return null
    // Highlight only the redundant blank lines: from the second newline to the last newline
    // (i.e. everything that will be removed by the edit except the preserved \n\n + indent).
    // We highlight the entire whitespace span for clarity.
    val startOffset = textRange.startOffset
    val endOffset = textRange.endOffset
    if (startOffset >= endOffset) return null
    return ConsecutiveBlankLineIssue(startOffset, endOffset)
}

private fun PsiElement.consecutiveBlankLineEdit(fileLength: Int): TextEdit? {
    val wsText = text ?: return null
    val startOffset = textRange.startOffset
    val endOffset = textRange.endOffset

    // Skip the EOF whitespace token — that's FileWhitespaceRule's territory.
    if (endOffset >= fileLength) return null

    val newlineCount = wsText.count { it == '\n' }
    // 1 blank line = \n\n (2 newlines); 2+ blank lines = 3+ newlines
    if (newlineCount < 3) return null

    // Preserve the trailing indentation of the last line in the whitespace token
    // (everything after the last \n).
    val trailingIndent = wsText.substringAfterLast('\n')
    val replacement = "\n\n$trailingIndent"

    // Idempotency: if the token already equals the replacement, no edit needed.
    if (wsText == replacement) return null

    return TextEdit(startOffset, endOffset, replacement)
}
