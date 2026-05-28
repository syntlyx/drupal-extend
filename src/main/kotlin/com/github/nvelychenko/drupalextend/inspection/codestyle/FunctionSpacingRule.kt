package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Function

internal object FunctionSpacingRule : DrupalCodeStyleRule {
    override val id: String = "function_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleFunctionSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        inspectBefore(function, context)
        inspectAfter(function, context)
    }

    private fun inspectBefore(function: Function, context: DrupalCodeStyleContext) {
        val anchor = function.docComment ?: function
        val document = anchor.document() ?: return
        val line = document.lineNumber(anchor)
        if (document.previousNonBlankLine(line)?.trim() == "<?php") return

        val blankLines = document.blankLinesBefore(line)
        if (blankLines == 1) return

        context.register(
            DrupalCodeStyleProblem(
                element = function,
                message = "Expected 1 blank line before function; $blankLines found",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = function.nameNode?.psi?.let { function.relativeRangeOf(it) },
                fixes = listOf(normalizeBlankLineBeforeFunctionFix())
            )
        )
    }

    private fun inspectAfter(function: Function, context: DrupalCodeStyleContext) {
        val document = function.document() ?: return
        val line = document.endLineNumber(function)
        document.nextNonBlankLine(line) ?: return

        val blankLines = document.blankLinesAfter(line)
        if (blankLines == 1) return

        context.register(
            DrupalCodeStyleProblem(
                element = function,
                message = "Expected 1 blank line after function; $blankLines found",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = function.nameNode?.psi?.let { function.relativeRangeOf(it) },
                fixes = listOf(normalizeBlankLineAfterFunctionFix())
            )
        )
    }

    private fun normalizeBlankLineBeforeFunctionFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize blank line before function") { _, descriptor ->
            val function = descriptor.psiElement as? Function ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val anchor = function.docComment ?: function
            val document = anchor.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val line = document.lineNumber(anchor)
            if (document.previousNonBlankLine(line)?.trim() == "<?php") return@DrupalCodeStyleTextEditQuickFix emptyList()

            document.normalizeBlankLinesBeforeEdit(line)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun normalizeBlankLineAfterFunctionFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize blank line after function") { _, descriptor ->
            val function = descriptor.psiElement as? Function ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = function.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val line = document.endLineNumber(function)
            document.nextNonBlankLine(line) ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            document.normalizeBlankLinesAfterEdit(line)?.let { listOf(it) } ?: emptyList()
        }
    }
}
