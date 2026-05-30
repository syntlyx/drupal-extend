package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.Function

internal object FunctionDocTagDescriptionLineRule : DrupalCodeStyleRule {
    override val id: String = "docblock_tag_description_line"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockTagDescriptionLineEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val docComment = function.docComment ?: return
        docComment.drupalDocBlockLines().forEachIndexed { index, line ->
            val inlineDescription = line.inlineTagDescription() ?: return@forEachIndexed
            val tagName = line.tagName() ?: return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                line,
                "@$tagName description must be on a continuation line",
                moveDescriptionToContinuationLineFix(index, inlineDescription, tagName)
            )
        }
    }

    private fun moveDescriptionToContinuationLineFix(
        lineIndex: Int,
        inlineDescription: InlineTagDescription,
        tagName: String,
    ): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Move @$tagName description to continuation line") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lines = docComment.drupalDocBlockLines()
            val line = lines.firstOrNull {
                it.tagName() == tagName && it.inlineTagDescription()?.description == inlineDescription.description
            } ?: lines.getOrNull(lineIndex) ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val description = line.inlineTagDescription() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = docComment.document()
            val lineSeparator = document?.lineSeparator() ?: "\n"
            val replacement = line.trimmedContent.substring(0, description.descriptionStart).trimEnd() +
                lineSeparator +
                "${line.indentation()}*   ${description.description}"

            listOf(
                TextEdit(
                    docComment.textRange.startOffset + line.trimmedContentStartOffset(),
                    docComment.textRange.startOffset + line.lineEndOffset,
                    replacement
                )
            )
        }
    }

    private fun DrupalDocBlockLine.inlineTagDescription(): InlineTagDescription? {
        if (tagName() == "param") {
            val paramTag = paramTag() ?: return null
            val descriptionStart = paramTag.descriptionStartOffset ?: return null
            val description = paramTag.description?.trim().orEmpty()
            if (description.isBlank()) return null

            return InlineTagDescription(descriptionStart, description)
        }

        if (tagName() != "return") return null
        val returnTag = returnTag() ?: return null
        val descriptionStart = returnTag.descriptionStartOffset ?: return null
        val description = returnTag.description?.trim().orEmpty()
        if (description.isBlank()) return null

        return InlineTagDescription(descriptionStart, description)
    }

    private data class InlineTagDescription(
        val descriptionStart: Int,
        val description: String,
    )
}
