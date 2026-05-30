package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Function

internal object FunctionDocParamNameRule : DrupalCodeStyleRule {
    override val id: String = "docblock_missing_param_name"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockMissingParamNameEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val docComment = function.docComment ?: return
        val paramLines = docComment.drupalDocBlockLines().filter { it.tagName() == "param" }
        paramLines.forEachIndexed { index, line ->
            if (line.hasParamName()) return@forEachIndexed
            val parameterName = function.parameters.getOrNull(index)?.phpDocVariableName() ?: return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                line,
                "@param tag is missing parameter name '$parameterName'",
                insertMissingParamNameFix(index, parameterName)
            )
        }
    }

    private fun insertMissingParamNameFix(paramIndex: Int, parameterName: String): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Insert missing @param name") { _, descriptor ->
            val docComment = descriptor.psiElement as? com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val line = docComment.drupalDocBlockLines()
                .filter { it.tagName() == "param" }
                .getOrNull(paramIndex)
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            if (line.hasParamName()) return@DrupalCodeStyleTextEditQuickFix emptyList()

            val insertOffset = line.paramTypeEndOffset() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOf(TextEdit(docComment.textRange.startOffset + insertOffset, " $parameterName"))
        }
    }

    private fun DrupalDocBlockLine.hasParamName(): Boolean {
        return paramTag()?.name?.let {
            it.startsWith("\$") || it.startsWith("...\$") || it.startsWith("&\$")
        } == true
    }

    private fun DrupalDocBlockLine.paramTypeEndOffset(): Int? {
        return paramTag()?.let { trimmedContentStartOffset() + it.typeEndOffset }
    }

    private fun com.jetbrains.php.lang.psi.elements.Parameter.phpDocVariableName(): String {
        return "\$" + name.removePrefix("\$")
    }
}
