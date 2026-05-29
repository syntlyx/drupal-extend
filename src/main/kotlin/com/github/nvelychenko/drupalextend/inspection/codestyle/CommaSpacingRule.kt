package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ParameterList

internal object CommaSpacingRule : DrupalCodeStyleRule {
    override val id: String = "comma_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleCommaSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        if (element !is ParameterList && element !is ArrayCreationExpression) return

        val edit = element.commaSpacingEdits().firstOrNull() ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = "Comma-separated lists must use single spaces",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(normalizeCommaSpacingFix())
            )
        )
    }

    private fun normalizeCommaSpacingFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize comma spacing") { _, descriptor ->
            descriptor.psiElement.commaSpacingEdits()
        }
    }
}

internal fun PsiElement.commaSpacingEdits(): List<TextEdit> {
    return directCommaElements()
        .mapNotNull { it.commaSpacingEdit() }
}

private fun PsiElement.directCommaElements(): List<PsiElement> {
    val commas = mutableListOf<PsiElement>()
    var child = firstChild
    while (child != null) {
        if (child.node.elementType == PhpTokenTypes.opCOMMA) {
            commas += child
        }
        child = child.nextSibling
    }

    return commas
}

private fun PsiElement.commaSpacingEdit(): TextEdit? {
    val previous = previousNonWhitespaceSibling() ?: return null
    val next = nextNonWhitespaceSibling() ?: return null
    val startOffset = previous.textRange.endOffset
    val endOffset = next.textRange.startOffset
    if (startOffset >= endOffset) return null

    val currentGap = containingFile.text.substring(startOffset, endOffset)
    if (currentGap.any { it == '\n' || it == '\r' }) return null

    val expectedGap = if (next.isClosingListToken()) "," else ", "
    if (currentGap == expectedGap) return null

    return TextEdit(startOffset, endOffset, expectedGap)
}

private fun PsiElement.previousNonWhitespaceSibling(): PsiElement? {
    var sibling = prevSibling
    while (sibling is PsiWhiteSpace) {
        sibling = sibling.prevSibling
    }

    return sibling
}

private fun PsiElement.nextNonWhitespaceSibling(): PsiElement? {
    var sibling = nextSibling
    while (sibling is PsiWhiteSpace) {
        sibling = sibling.nextSibling
    }

    return sibling
}

private fun PsiElement.isClosingListToken(): Boolean {
    return node.elementType == PhpTokenTypes.chRPAREN || node.elementType == PhpTokenTypes.chRBRACKET
}
