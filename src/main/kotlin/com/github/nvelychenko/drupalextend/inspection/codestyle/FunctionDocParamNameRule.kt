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
        val lines = docComment.drupalDocBlockLines()
        var paramIndex = 0
        lines.forEachIndexed { lineIndex, line ->
            if (line.tagName() != "param") return@forEachIndexed

            val currentParamIndex = paramIndex
            paramIndex++
            if (lines.paramTagHasName(lineIndex)) return@forEachIndexed
            val parameterName = function.parameters.getOrNull(currentParamIndex)?.phpDocVariableName() ?: return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                line,
                "@param tag is missing parameter name '$parameterName'",
                insertMissingParamNameFix(currentParamIndex, parameterName)
            )
        }
    }

    private fun insertMissingParamNameFix(paramIndex: Int, parameterName: String): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Insert missing @param name") { _, descriptor ->
            val docComment = descriptor.psiElement as? com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lines = docComment.drupalDocBlockLines()
            val lineIndex = lines.indices
                .filter { lines[it].tagName() == "param" }
                .getOrNull(paramIndex)
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            if (lines.paramTagHasName(lineIndex)) return@DrupalCodeStyleTextEditQuickFix emptyList()

            val insertOffset = lines.paramTagNameInsertOffset(lineIndex)
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOf(TextEdit(docComment.textRange.startOffset + insertOffset, " $parameterName"))
        }
    }

    private fun com.jetbrains.php.lang.psi.elements.Parameter.phpDocVariableName(): String {
        return "\$" + name.removePrefix("\$")
    }
}
