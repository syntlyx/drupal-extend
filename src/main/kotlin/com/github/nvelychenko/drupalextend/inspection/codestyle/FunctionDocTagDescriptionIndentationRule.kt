package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.Function

internal object FunctionDocTagDescriptionIndentationRule : DrupalCodeStyleRule {
    override val id: String = "docblock_tag_description_indentation"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockTagDescriptionIndentationEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val docComment = function.docComment ?: return
        val lines = docComment.drupalDocBlockLines()
        lines.forEachIndexed { index, line ->
            val tagName = line.tagName()
            if (tagName != "param" && tagName != "return") return@forEachIndexed

            descriptionLines(lines, index).forEach { descriptionLine ->
                if (descriptionLine.hasExpectedDescriptionIndentation()) return@forEach

                context.registerDocBlockProblem(
                    docComment,
                    descriptionLine,
                    "@$tagName description must be indented by two spaces",
                    normalizeDescriptionIndentationFix(index, tagName)
                )
            }
        }
    }

    private fun normalizeDescriptionIndentationFix(
        tagIndex: Int,
        tagName: String,
    ): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize @$tagName description indentation") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lines = docComment.drupalDocBlockLines()
            val descriptionLine = descriptionLines(lines, tagIndex).firstOrNull {
                !it.hasExpectedDescriptionIndentation()
            } ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            listOf(
                TextEdit(
                    docComment.textRange.startOffset + descriptionLine.contentStartOffset,
                    docComment.textRange.startOffset + descriptionLine.lineEndOffset,
                    DESCRIPTION_INDENT + descriptionLine.trimmedContent
                )
            )
        }
    }

    private fun descriptionLines(lines: List<DrupalDocBlockLine>, tagIndex: Int): List<DrupalDocBlockLine> {
        return lines
            .drop(tagIndex + 1)
            .takeWhile { !it.isTag }
            .filter { !it.isBlankContent && it.normalizedTrimmedContent().isNotBlank() }
    }

    private fun DrupalDocBlockLine.hasExpectedDescriptionIndentation(): Boolean {
        return content == DESCRIPTION_INDENT + trimmedContent
    }

    private const val DESCRIPTION_INDENT = "  "
}
