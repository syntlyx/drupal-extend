package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.PhpSwitch

internal object BlockBlankLineRule : DrupalCodeStyleRule {
    override val id: String = "block_blank_lines"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleBlockBlankLinesEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        if (!element.isBlockBlankLineTarget()) return
        val document = element.document() ?: return

        element.blockBlankLineIssues(document).forEach { issue ->
            context.register(
                DrupalCodeStyleProblem(
                    element = element,
                    message = issue.message,
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    rangeInElement = element.relativeRange(issue.highlightStartOffset, issue.highlightEndOffset),
                    fixes = listOf(normalizeBlockBlankLinesFix())
                )
            )
        }
    }

    private fun normalizeBlockBlankLinesFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize block blank lines") { _, descriptor ->
            val document = descriptor.psiElement.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            descriptor.psiElement.blockBlankLineEdits(document)
        }
    }
}

internal fun PsiElement.blockBlankLineEdits(document: Document): List<TextEdit> {
    return blockBlankLineIssues(document)
        .map { it.edit }
        .distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
}

private fun PsiElement.blockBlankLineIssues(document: Document): List<BlockBlankLineIssue> {
    if (!isBlockBlankLineTarget()) return emptyList()
    val braces = blockBraces() ?: return emptyList()
    val openLine = document.getLineNumber(braces.openOffset)
    val closeLine = document.getLineNumber(braces.closeOffset)
    if (openLine >= closeLine) return emptyList()

    val issues = mutableListOf<BlockBlankLineIssue>()
    document.removeBlankLinesAfterEdit(openLine)?.let { edit ->
        issues += BlockBlankLineIssue(
            message = "Block opening braces must not be followed by a blank line",
            edit = edit,
            highlightStartOffset = braces.openOffset,
            highlightEndOffset = braces.openOffset + 1,
        )
    }

    document.removeBlankLinesBeforeEdit(closeLine)?.let { edit ->
        issues += BlockBlankLineIssue(
            message = "Block closing braces must not be preceded by a blank line",
            edit = edit,
            highlightStartOffset = braces.closeOffset,
            highlightEndOffset = braces.closeOffset + 1,
        )
    }

    return issues
}

private data class BlockBlankLineIssue(
    val message: String,
    val edit: TextEdit,
    val highlightStartOffset: Int,
    val highlightEndOffset: Int,
)

private data class BlockBraces(
    val openOffset: Int,
    val closeOffset: Int,
)

private fun PsiElement.isBlockBlankLineTarget(): Boolean {
    return this is Function || this is ControlStatement || this is Else || this is PhpSwitch
}

private fun PsiElement.blockBraces(): BlockBraces? {
    val braceElements = PsiTreeUtil.collectElements(this) { element ->
        element.node.elementType == PhpTokenTypes.chLBRACE ||
            element.node.elementType == PhpTokenTypes.chRBRACE
    }.sortedBy { it.textRange.startOffset }

    val openBrace = braceElements.firstOrNull {
        it.node.elementType == PhpTokenTypes.chLBRACE
    } ?: return null

    var depth = 0
    for (brace in braceElements.dropWhile { it != openBrace }) {
        when (brace.node.elementType) {
            PhpTokenTypes.chLBRACE -> depth++
            PhpTokenTypes.chRBRACE -> {
                depth--
                if (depth == 0) {
                    return BlockBraces(openBrace.textRange.startOffset, brace.textRange.startOffset)
                }
            }
        }
    }

    return null
}
