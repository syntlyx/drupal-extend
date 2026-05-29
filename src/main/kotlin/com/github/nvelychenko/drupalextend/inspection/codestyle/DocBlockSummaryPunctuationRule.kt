package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockSummaryPunctuationRule : DrupalCodeStyleRule {
    override val id: String = "docblock_summary_punctuation"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockSummaryPunctuationEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        val summaryLine = docComment.drupalDocBlockLines()
            .takeWhile { !it.isTag }
            .firstOrNull { it.isSummaryLine() }
            ?: return
        if (summaryLine.normalizedTrimmedContent().endsWithDocBlockSentencePunctuation()) return

        context.registerDocBlockProblem(
            docComment,
            summaryLine,
            "DocBlock summary should end with punctuation",
            addSummaryPunctuationFix()
        )
    }

    private fun addSummaryPunctuationFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Add DocBlock summary punctuation") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val summaryLine = docComment.drupalDocBlockLines()
                .takeWhile { !it.isTag }
                .firstOrNull {
                    it.isSummaryLine() && !it.normalizedTrimmedContent().endsWithDocBlockSentencePunctuation()
                }
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            listOf(TextEdit(docComment.textRange.startOffset + summaryLine.contentEndOffset(), "."))
        }
    }
}
