package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression

internal object OperatorSpacingRule : DrupalCodeStyleRule {
    override val id: String = "operator_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleOperatorSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        when (element) {
            is AssignmentExpression -> inspectAssignment(element, context)
            is ArrayHashElement -> inspectArrayHash(element, context)
        }
    }

    private fun inspectAssignment(expression: AssignmentExpression, context: DrupalCodeStyleContext) {
        val edit = expression.assignmentOperatorSpacingEdit() ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = expression,
                message = "Assignment operator must be surrounded by single spaces",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = expression.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(normalizeOperatorSpacingFix())
            )
        )
    }

    private fun inspectArrayHash(element: ArrayHashElement, context: DrupalCodeStyleContext) {
        val edit = element.arrayArrowSpacingEdit() ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = "Array key/value operator must be surrounded by single spaces",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(normalizeOperatorSpacingFix())
            )
        )
    }

    private fun normalizeOperatorSpacingFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize operator spacing") { _, descriptor ->
            when (val element = descriptor.psiElement) {
                is AssignmentExpression -> element.assignmentOperatorSpacingEdit()?.let { listOf(it) } ?: emptyList()
                is ArrayHashElement -> element.arrayArrowSpacingEdit()?.let { listOf(it) } ?: emptyList()
                else -> emptyList()
            }
        }
    }
}

internal fun AssignmentExpression.assignmentOperatorSpacingEdit(): TextEdit? {
    val variable = variable ?: return null
    val value = value ?: return null

    return operatorSpacingEdit(variable.textRange.endOffset, value.textRange.startOffset, ASSIGNMENT_OPERATORS)
}

internal fun ArrayHashElement.arrayArrowSpacingEdit(): TextEdit? {
    val key = key ?: return null
    val value = value ?: return null

    return operatorSpacingEdit(key.textRange.endOffset, value.textRange.startOffset, setOf("=>"))
}

private fun PsiElement.operatorSpacingEdit(
    startOffset: Int,
    endOffset: Int,
    supportedOperators: Set<String>,
): TextEdit? {
    if (startOffset >= endOffset) return null
    val gap = containingFile.text.substring(startOffset, endOffset)
    if (gap.any { it == '\n' || it == '\r' }) return null

    val operator = gap.trim()
    if (operator !in supportedOperators) return null

    val expectedGap = " $operator "
    if (gap == expectedGap) return null

    return TextEdit(startOffset, endOffset, expectedGap)
}

private val ASSIGNMENT_OPERATORS = setOf(
    "=",
    "=&",
    "+=",
    "-=",
    "*=",
    "/=",
    "%=",
    ".=",
    "&=",
    "|=",
    "^=",
    "<<=",
    ">>=",
    "??=",
)
