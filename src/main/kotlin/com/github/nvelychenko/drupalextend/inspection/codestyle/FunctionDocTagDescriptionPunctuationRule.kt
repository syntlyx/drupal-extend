package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.Function

internal object FunctionDocTagDescriptionPunctuationRule : DrupalCodeStyleRule {
    override val id: String = "docblock_tag_description_punctuation"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockTagDescriptionPunctuationEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val docComment = function.docComment ?: return
        val lines = docComment.drupalDocBlockLines()
        lines.forEachIndexed { index, line ->
            val tagName = line.tagName()
            if (tagName != "param" && tagName != "return") return@forEachIndexed

            val descriptionLine = lines.tagDescriptionPunctuationLine(index, tagName) ?: return@forEachIndexed
            if (descriptionLine.normalizedTrimmedContent().endsWithDocBlockSentencePunctuation()) return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                descriptionLine,
                "@$tagName description should end with punctuation",
                addTagDescriptionPunctuationFix(index, tagName)
            )
        }
    }

    private fun addTagDescriptionPunctuationFix(
        tagIndex: Int,
        tagName: String,
    ): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Add @$tagName description punctuation") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lines = docComment.drupalDocBlockLines()
            val descriptionLine = lines.tagDescriptionPunctuationLine(tagIndex, tagName)
                ?: lines.firstTagDescriptionPunctuationLine(tagName)
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            if (descriptionLine.normalizedTrimmedContent().endsWithDocBlockSentencePunctuation()) {
                return@DrupalCodeStyleTextEditQuickFix emptyList()
            }

            listOf(TextEdit(docComment.textRange.startOffset + descriptionLine.contentEndOffset(), "."))
        }
    }
}

internal fun List<DrupalDocBlockLine>.tagDescriptionPunctuationLine(
    tagIndex: Int,
    tagName: String,
): DrupalDocBlockLine? {
    val tagLine = getOrNull(tagIndex)?.takeIf { it.tagName() == tagName } ?: return null
    val continuationDescriptionLines = drop(tagIndex + 1)
        .takeWhile { !it.isTag && !it.isBlankContent }
        .filter { it.normalizedTrimmedContent().isNotBlank() }

    if (continuationDescriptionLines.isNotEmpty()) {
        return continuationDescriptionLines.last()
    }

    return tagLine.takeIf { it.hasInlineTagDescription() }
}

private fun List<DrupalDocBlockLine>.firstTagDescriptionPunctuationLine(tagName: String): DrupalDocBlockLine? {
    forEachIndexed { index, line ->
        if (line.tagName() != tagName) return@forEachIndexed
        val descriptionLine = tagDescriptionPunctuationLine(index, tagName) ?: return@forEachIndexed
        if (!descriptionLine.normalizedTrimmedContent().endsWithDocBlockSentencePunctuation()) {
            return descriptionLine
        }
    }

    return null
}

internal fun DrupalDocBlockLine.hasInlineTagDescription(): Boolean {
    return when (tagName()) {
        "param" -> PARAM_WITH_INLINE_DESCRIPTION.matches(trimmedContent)
        "return" -> RETURN_WITH_INLINE_DESCRIPTION.matches(trimmedContent)
        else -> false
    }
}

private val PARAM_WITH_INLINE_DESCRIPTION =
    Regex("""^@param\s+\S+\s+(?:&?\${'$'}|\.{3}\${'$'})\S+\s+.+\S\s*${'$'}""")

private val RETURN_WITH_INLINE_DESCRIPTION =
    Regex("""^@return\s+\S+\s+.+\S\s*${'$'}""")
