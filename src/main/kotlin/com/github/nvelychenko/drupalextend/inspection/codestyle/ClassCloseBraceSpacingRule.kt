package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpClass

internal object ClassCloseBraceSpacingRule : DrupalCodeStyleRule {
    override val id: String = "class_close_brace_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleClassCloseBraceSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val phpClass = element as? PhpClass ?: return
        if (phpClass.isAnonymous) return

        val document = phpClass.document() ?: return
        val classStartLine = document.lineNumber(phpClass)
        val closeBraceLine = document.endLineNumber(phpClass)
        if (closeBraceLine <= classStartLine) return
        if (!document.lineText(closeBraceLine).trimStart().startsWith("}")) return
        if (document.blankLinesBefore(closeBraceLine) > 0) return
        val closeBraceOffset = document.closeBraceOffset(closeBraceLine) ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = phpClass,
                message = "The closing brace for the class must have an empty line before it",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = phpClass.relativeRange(closeBraceOffset, closeBraceOffset + 1),
                fixes = listOf(insertBlankLineBeforeClassClosingBraceFix())
            )
        )
    }

    private fun insertBlankLineBeforeClassClosingBraceFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Insert blank line before class closing brace") { _, descriptor ->
            val phpClass = descriptor.psiElement as? PhpClass ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = phpClass.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val classStartLine = document.lineNumber(phpClass)
            val closeBraceLine = document.endLineNumber(phpClass)
            if (closeBraceLine <= classStartLine) return@DrupalCodeStyleTextEditQuickFix emptyList()
            if (!document.lineText(closeBraceLine).trimStart().startsWith("}")) {
                return@DrupalCodeStyleTextEditQuickFix emptyList()
            }

            document.normalizeBlankLinesBeforeEdit(closeBraceLine)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun com.intellij.openapi.editor.Document.closeBraceOffset(line: Int): Int? {
        val lineText = lineText(line)
        val closeBraceColumn = lineText.indexOf('}').takeIf { it >= 0 } ?: return null
        return getLineStartOffset(line) + closeBraceColumn
    }
}
