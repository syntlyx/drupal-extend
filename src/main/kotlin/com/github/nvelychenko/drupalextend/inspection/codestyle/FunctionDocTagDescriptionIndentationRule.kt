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

            val descriptionLines = lines.tagDescriptionLines(index)
            descriptionLines.forEachIndexed { descriptionLineIndex, descriptionLine ->
                if (descriptionLine.hasExpectedDescriptionIndentation(descriptionLines, descriptionLineIndex)) {
                    return@forEachIndexed
                }

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
            val descriptionLines = lines.tagDescriptionLines(tagIndex)
            val invalidDescriptionLine = descriptionLines.firstIndexedOrNull { index, line ->
                !line.hasExpectedDescriptionIndentation(descriptionLines, index)
            } ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val descriptionLine = invalidDescriptionLine.value

            listOf(
                TextEdit(
                    docComment.textRange.startOffset + descriptionLine.contentStartOffset,
                    docComment.textRange.startOffset + descriptionLine.lineEndOffset,
                    descriptionLine.expectedDescriptionIndentation(descriptionLines.take(invalidDescriptionLine.index)) +
                        descriptionLine.trimmedContent
                )
            )
        }
    }

    private fun DrupalDocBlockLine.hasExpectedDescriptionIndentation(
        descriptionLines: List<DrupalDocBlockLine>,
        index: Int,
    ): Boolean {
        return content == expectedDescriptionIndentation(descriptionLines.take(index)) + trimmedContent
    }

    private fun DrupalDocBlockLine.expectedDescriptionIndentation(
        previousDescriptionLines: List<DrupalDocBlockLine>,
    ): String {
        val currentIndent = descriptionIndentationWidth()
        val previousLine = previousDescriptionLines.lastOrNull()
        if (isDocListItem() && previousLine?.normalizedTrimmedContent()?.endsWith(":") == true) {
            return " ".repeat(previousLine.descriptionIndentationWidth() + DESCRIPTION_INDENT.length)
        }

        if (isDocListItem() && previousLine?.isDocListItem() == true) {
            return " ".repeat(previousLine.descriptionIndentationWidth())
        }

        if (isDocListItem() && currentIndent >= DESCRIPTION_INDENT.length && currentIndent % DESCRIPTION_INDENT.length == 0) {
            return " ".repeat(currentIndent)
        }

        return DESCRIPTION_INDENT
    }

    private fun DrupalDocBlockLine.descriptionIndentationWidth(): Int {
        return content.takeWhile { it == ' ' }.length
    }

    private fun DrupalDocBlockLine.isDocListItem(): Boolean {
        return trimmedContent.startsWith("- ")
    }

    private fun <T> List<T>.firstIndexedOrNull(predicate: (Int, T) -> Boolean): IndexedValue<T>? {
        forEachIndexed { index, item ->
            if (predicate(index, item)) return IndexedValue(index, item)
        }

        return null
    }

    private const val DESCRIPTION_INDENT = "  "
}
