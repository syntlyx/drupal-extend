package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes

internal object CastSpacingRule : DrupalCodeStyleRule {
    override val id: String = "cast_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleCastSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val issue = element.castSpacingIssue() ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = "Casts must be followed by a single space",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(issue.highlightStartOffset, issue.highlightEndOffset),
                fixes = listOf(normalizeCastSpacingFix())
            )
        )
    }

    private fun normalizeCastSpacingFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize cast spacing") { _, descriptor ->
            descriptor.psiElement.castSpacingEdit()?.let { listOf(it) } ?: emptyList()
        }
    }
}

internal fun PsiElement.castSpacingEdit(): TextEdit? {
    return castSpacingIssue()?.edit
}

private fun PsiElement.castSpacingIssue(): CastSpacingIssue? {
    if (node.elementType != PhpElementTypes.CAST_EXPRESSION) return null

    val castOperator = directCastOperator() ?: return null
    val operand = castOperator.nextNonWhitespaceSibling() ?: return null
    val startOffset = castOperator.textRange.endOffset
    val endOffset = operand.textRange.startOffset
    if (startOffset > endOffset) return null

    val gap = containingFile.text.substring(startOffset, endOffset)
    if (gap.any { it == '\n' || it == '\r' }) return null
    if (gap == " ") return null

    return CastSpacingIssue(
        edit = TextEdit(startOffset, endOffset, " "),
        highlightStartOffset = castOperator.textRange.startOffset,
        highlightEndOffset = castOperator.textRange.endOffset,
    )
}

private data class CastSpacingIssue(
    val edit: TextEdit,
    val highlightStartOffset: Int,
    val highlightEndOffset: Int,
)

private fun PsiElement.directCastOperator(): PsiElement? {
    var child = firstChild
    while (child != null) {
        if (PhpTokenTypes.CAST_OPERATORS.contains(child.node.elementType)) return child
        child = child.nextSibling
    }

    return null
}

private fun PsiElement.nextNonWhitespaceSibling(): PsiElement? {
    var sibling = nextSibling
    while (sibling is PsiWhiteSpace) {
        sibling = sibling.nextSibling
    }

    return sibling
}
