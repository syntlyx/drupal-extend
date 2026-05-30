package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockContentAfterOpenRule : DrupalCodeStyleRule {
    override val id: String = "docblock_content_after_open"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockContentAfterOpenEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        if (docComment.isInlineVariableDocBlock()) return

        val openingLine = docComment.drupalDocBlockLines().firstOrNull() ?: return
        if (openingLine.normalizedTrimmedContent().isBlank()) return

        context.registerDocBlockProblem(
            docComment,
            openingLine,
            "DocBlock opening line must not contain content",
            moveOpeningContentToSummaryFix()
        )
    }

    private fun moveOpeningContentToSummaryFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Move DocBlock summary to its own line") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val openingLine = docComment.drupalDocBlockLines().firstOrNull() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val summary = openingLine.normalizedTrimmedContent()
            if (summary.isBlank()) return@DrupalCodeStyleTextEditQuickFix emptyList()

            val document = docComment.document()
            val lineSeparator = document?.lineSeparator() ?: "\n"
            val indentation = openingLine.indentation()
            val replacement = if (openingLine.trimmedContent.endsWith("*/")) {
                "$indentation/**$lineSeparator$indentation * $summary$lineSeparator$indentation */"
            } else {
                "$indentation/**$lineSeparator$indentation * $summary"
            }

            listOf(
                TextEdit(
                    docComment.textRange.startOffset + openingLine.lineStartOffset,
                    docComment.textRange.startOffset + openingLine.lineEndOffset,
                    replacement
                )
            )
        }
    }
}
