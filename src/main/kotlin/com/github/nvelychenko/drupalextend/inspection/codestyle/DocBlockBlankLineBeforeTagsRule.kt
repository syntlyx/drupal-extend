package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockBlankLineBeforeTagsRule : DrupalCodeStyleRule {
    override val id: String = "docblock_blank_line_before_tags"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        val lines = docComment.drupalDocBlockLines()
        val tagLineIndex = lines.indexOfFirst { it.isTag }
        if (tagLineIndex <= 0) return

        val previousContentLine = lines
            .take(tagLineIndex)
            .lastOrNull { !it.isBlankContent }
            ?: return
        val previousLineIndex = lines.indexOf(previousContentLine)
        if (previousLineIndex != tagLineIndex - 1) return

        val tagLine = lines[tagLineIndex]
        context.registerDocBlockProblem(
            docComment,
            tagLine,
            "DocBlock tags should be separated from the description by a blank line",
            insertBlankLineBeforeTagsFix()
        )
    }

    private fun insertBlankLineBeforeTagsFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Insert blank DocBlock line before tags") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lines = docComment.drupalDocBlockLines()
            val tagLineIndex = lines.indexOfFirst { it.isTag }
            if (tagLineIndex <= 0) return@DrupalCodeStyleTextEditQuickFix emptyList()

            val previousContentLine = lines
                .take(tagLineIndex)
                .lastOrNull { !it.isBlankContent }
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val previousLineIndex = lines.indexOf(previousContentLine)
            if (previousLineIndex != tagLineIndex - 1) return@DrupalCodeStyleTextEditQuickFix emptyList()

            val tagLine = lines[tagLineIndex]
            val document = docComment.document()
            val lineSeparator = document?.lineSeparator() ?: "\n"
            listOf(
                TextEdit(
                    docComment.textRange.startOffset + tagLine.lineStartOffset,
                    "${tagLine.indentation()}*$lineSeparator"
                )
            )
        }
    }
}
