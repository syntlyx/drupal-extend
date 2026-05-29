package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.If as PhpIf

internal object ElseIfSyntaxRule : DrupalCodeStyleRule {
    override val id: String = "elseif_syntax"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleElseIfSyntaxEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val elseElement = element as? Else ?: return
        val edit = elseElement.elseIfSyntaxEdit() ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = elseElement,
                message = "else if constructs must use elseif",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = elseElement.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(convertElseIfToElseifFix())
            )
        )
    }

    private fun convertElseIfToElseifFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Convert else if to elseif") { _, descriptor ->
            val elseElement = descriptor.psiElement as? Else ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOfNotNull(elseElement.elseIfSyntaxEdit())
        }
    }
}

internal fun Else.elseIfSyntaxEdit(): TextEdit? {
    if (statement !is PhpIf) return null

    val fileText = containingFile.text
    val elseStartOffset = textRange.startOffset
    if (!fileText.regionMatches(elseStartOffset, "else", 0, "else".length)) return null

    val ifStartOffset = nextNonWhitespaceSameLine(fileText, elseStartOffset + "else".length) ?: return null
    if (!fileText.regionMatches(ifStartOffset, "if", 0, "if".length)) return null

    val gap = fileText.substring(elseStartOffset + "else".length, ifStartOffset)
    if (gap.isEmpty() || gap.any { !it.isWhitespace() || it == '\n' || it == '\r' }) return null

    return TextEdit(elseStartOffset, ifStartOffset + "if".length, "elseif")
}

private fun nextNonWhitespaceSameLine(fileText: String, startOffset: Int): Int? {
    var offset = startOffset
    while (offset < fileText.length && fileText[offset].isWhitespace()) {
        if (fileText[offset] == '\n' || fileText[offset] == '\r') return null
        offset++
    }

    return offset.takeIf { it < fileText.length }
}
