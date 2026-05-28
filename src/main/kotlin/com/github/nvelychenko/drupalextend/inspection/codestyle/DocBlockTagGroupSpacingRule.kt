package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockTagGroupSpacingRule : DrupalCodeStyleRule {
    override val id: String = "docblock_tag_group_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockTagGroupSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        val lines = docComment.drupalDocBlockLines()
        lines.missingTagGroupSeparatorLines().forEach { line ->
            context.registerDocBlockProblem(
                docComment,
                line,
                "DocBlock tag groups must be separated by a blank line",
                insertTagGroupBlankLinesFix()
            )
        }
    }

    private fun insertTagGroupBlankLinesFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Insert blank line between DocBlock tag groups") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lineSeparator = docComment.document()?.lineSeparator() ?: "\n"

            docComment.drupalDocBlockLines()
                .missingTagGroupSeparatorLines()
                .map { line ->
                    TextEdit(
                        docComment.textRange.startOffset + line.lineStartOffset,
                        "${line.indentation()}*$lineSeparator"
                    )
                }
        }
    }

    private fun List<DrupalDocBlockLine>.missingTagGroupSeparatorLines(): List<DrupalDocBlockLine> {
        return mapIndexedNotNull { index, line ->
            val currentGroup = line.tagGroup() ?: return@mapIndexedNotNull null
            val previousGroup = previousTagGroup(index) ?: return@mapIndexedNotNull null
            if (currentGroup == previousGroup) return@mapIndexedNotNull null
            if (getOrNull(index - 1)?.isBlankContent == true) return@mapIndexedNotNull null

            line
        }
    }

    private fun List<DrupalDocBlockLine>.previousTagGroup(index: Int): String? {
        for (lineIndex in index - 1 downTo 0) {
            val line = get(lineIndex)
            if (line.isBlankContent || line.isOpeningLine() || line.isClosingLine()) return null
            line.tagGroup()?.let { return it }
        }

        return null
    }
}
