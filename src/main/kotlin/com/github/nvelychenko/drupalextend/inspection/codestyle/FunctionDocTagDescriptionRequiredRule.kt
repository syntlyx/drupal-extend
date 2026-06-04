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
            if (tagName !in REQUIRED_DESCRIPTION_TAGS) return@forEachIndexed
            if (tagName == "param" && lines.paramTagHasDescription(index)) return@forEachIndexed
            if (line.hasInlineDescription()) return@forEachIndexed
            if (lines.tagDescriptionLines(index).isNotEmpty()) return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                line,
                "@$tagName tag is missing description"
            )
        }
    }

    private fun DrupalDocBlockLine.hasInlineDescription(): Boolean {
        return when (tagName()) {
            "param" -> paramTag()?.description?.isNotBlank() == true
            "return" -> returnTag()?.description?.isNotBlank() == true
            else -> false
        }
    }

    private val REQUIRED_DESCRIPTION_TAGS = setOf("param", "return")
}
