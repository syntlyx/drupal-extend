package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Function

internal object FunctionDocTagDescriptionRequiredRule : DrupalCodeStyleRule {
    override val id: String = "docblock_tag_description_required"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockTagDescriptionRequiredEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val docComment = function.docComment ?: return
        val lines = docComment.drupalDocBlockLines()
        lines.forEachIndexed { index, line ->
            val tagName = line.tagName()
            if (tagName != "param" && tagName != "return") return@forEachIndexed
            if (line.hasInlineDescription()) return@forEachIndexed
            if (lines.descriptionLines(index).isNotEmpty()) return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                line,
                "@$tagName tag is missing description"
            )
        }
    }

    private fun List<DrupalDocBlockLine>.descriptionLines(tagIndex: Int): List<DrupalDocBlockLine> {
        return drop(tagIndex + 1)
            .takeWhile { !it.isTag && !it.isBlankContent }
            .filter { !it.isBlankContent && it.normalizedTrimmedContent().isNotBlank() }
    }

    private fun DrupalDocBlockLine.hasInlineDescription(): Boolean {
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
}
