package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockMissingSummaryRule : DrupalCodeStyleRule {
    override val id: String = "docblock_missing_summary"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockMissingSummaryEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        val lines = docComment.drupalDocBlockLines()
        val summaryArea = lines.takeWhile { !it.isTag }
        if (summaryArea.any { it.isSummaryLine(allowInheritDoc = true) }) return

        val anchor = lines.firstOrNull { it.isTag } ?: lines.firstOrNull() ?: return
        context.registerDocBlockProblem(
            docComment,
            anchor,
            "DocBlock is missing a short summary"
        )
    }
}
