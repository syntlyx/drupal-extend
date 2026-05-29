package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ConstantReference

internal object ConstantCasingRule : DrupalCodeStyleRule {
    override val id: String = "constant_casing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleConstantCasingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val constant = element as? ConstantReference ?: return
        val edit = constant.constantCasingEdit() ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = constant,
                message = "TRUE, FALSE, and NULL constants must be uppercase",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = constant.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(normalizeConstantCasingFix())
            )
        )
    }

    private fun normalizeConstantCasingFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize constant casing") { _, descriptor ->
            (descriptor.psiElement as? ConstantReference)?.constantCasingEdit()?.let { listOf(it) }
                ?: emptyList()
        }
    }
}

internal fun PsiFile.constantCasingEdits(): List<TextEdit> {
    return PsiTreeUtil.findChildrenOfType(this, ConstantReference::class.java)
        .mapNotNull { it.constantCasingEdit() }
}

private fun ConstantReference.constantCasingEdit(): TextEdit? {
    val expected = text.uppercase().takeIf { it in DRUPAL_UPPERCASE_CONSTANTS } ?: return null
    if (text == expected) return null

    return TextEdit(textRange.startOffset, textRange.endOffset, expected)
}

private val DRUPAL_UPPERCASE_CONSTANTS = setOf("TRUE", "FALSE", "NULL")
