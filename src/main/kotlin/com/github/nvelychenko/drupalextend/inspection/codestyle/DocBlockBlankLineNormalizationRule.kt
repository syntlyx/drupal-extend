package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockBlankLineNormalizationRule : DrupalCodeStyleRule {
    override val id: String = "docblock_blank_line_normalization"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockBlankLineNormalizationEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        if (docComment.isInlineVariableDocBlock()) return

        val lines = docComment.drupalDocBlockLines()
        if (!lines.hasSemanticContent()) return

        lines.forEachIndexed { index, line ->
            if (!line.isBodyBlankLine()) return@forEachIndexed
            val isRedundant = lines.shouldRemoveBlankLine(index)
            val hasTrailingWhitespace = line.rawText != line.normalizedBlankLineText()
            if (!isRedundant && !hasTrailingWhitespace) return@forEachIndexed

            context.registerDocBlockProblem(
                docComment,
                line,
                if (isRedundant) {
                    "DocBlock contains a redundant blank line"
                } else {
                    "DocBlock blank line must not contain trailing whitespace"
                },
                normalizeDocBlockBlankLinesFix()
            )
        }
    }

    private fun normalizeDocBlockBlankLinesFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize DocBlock blank lines") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val normalizedText = docComment.normalizedBlankLineText() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            listOf(
                TextEdit(
                    docComment.textRange.startOffset,
                    docComment.textRange.endOffset,
                    normalizedText
                )
            )
        }
    }

    private fun PhpDocComment.normalizedBlankLineText(): String? {
        val lines = drupalDocBlockLines()
        if (!lines.hasSemanticContent()) return null

        val normalizedLines = lines.mapIndexedNotNull { index, line ->
            when {
                line.isBodyBlankLine() && lines.shouldRemoveBlankLine(index) -> null
                line.isBodyBlankLine() -> line.normalizedBlankLineText()
                else -> line.rawText
            }
        }
        val normalizedText = normalizedLines.joinToString(document()?.lineSeparator() ?: "\n")
        if (normalizedText == text) return null

        return normalizedText
    }

    private fun List<DrupalDocBlockLine>.hasSemanticContent(): Boolean {
        return any { !it.isOpeningLine() && !it.isClosingLine() && !it.isBlankContent }
    }

    private fun DrupalDocBlockLine.isBodyBlankLine(): Boolean {
        return isBlankContent && !isOpeningLine() && !isClosingLine()
    }

    private fun List<DrupalDocBlockLine>.shouldRemoveBlankLine(index: Int): Boolean {
        val previousLine = previousMeaningfulLine(index) ?: return true
        val nextLine = nextMeaningfulLine(index) ?: return true
        if (previousLine.isOpeningLine() || nextLine.isClosingLine()) return true
        if (getOrNull(index - 1)?.isBodyBlankLine() == true) return true

        val previousTagGroup = previousTagGroup(index)
        val nextTagGroup = nextLine.tagGroup()
        if (previousTagGroup != null && nextTagGroup != null) {
            return previousTagGroup == nextTagGroup
        }
        if (previousTagGroup != null && nextTagGroup == null) {
            return true
        }

        return false
    }

    private fun List<DrupalDocBlockLine>.previousMeaningfulLine(index: Int): DrupalDocBlockLine? {
        for (lineIndex in index - 1 downTo 0) {
            val line = get(lineIndex)
            if (!line.isBodyBlankLine()) return line
        }

        return null
    }

    private fun List<DrupalDocBlockLine>.nextMeaningfulLine(index: Int): DrupalDocBlockLine? {
        for (lineIndex in index + 1 until size) {
            val line = get(lineIndex)
            if (!line.isBodyBlankLine()) return line
        }

        return null
    }

    private fun List<DrupalDocBlockLine>.previousTagGroup(index: Int): String? {
        for (lineIndex in index - 1 downTo 0) {
            val line = get(lineIndex)
            if (line.isOpeningLine() || line.isClosingLine() || line.isBodyBlankLine()) return null
            line.tagGroup()?.let { return it }
        }

        return null
    }
}
