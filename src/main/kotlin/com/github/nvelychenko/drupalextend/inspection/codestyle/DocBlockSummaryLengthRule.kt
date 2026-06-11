package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

/**
 * Reports a DocBlock summary (first non-blank, non-tag text line) whose
 * physical line length exceeds 80 characters.
 *
 * The 80-character limit is measured from the start of the raw source line,
 * including leading indentation and the `* ` marker — exactly as a
 * line-length linter would count it.
 *
 * Skips:
 *   - Summaries that contain a URL (http:// or https://).
 *   - `@file` docblocks whose summary is identified by looking past the
 *     `@file` tag line (handled via `docBlockSummaryLines()`).
 *   - Docblocks with no summary (another rule handles those).
 *   - `{@inheritdoc}` / `{@inheritDoc}` lines.
 *
 * No quick fix is offered: automatically re-wrapping prose is unsafe.
 */
internal object DocBlockSummaryLengthRule : DrupalCodeStyleRule {
    override val id: String = "doc_block_summary_length"

    private const val MAX_LENGTH = 80

    private val URL_PATTERN = Regex("""https?://""")

    override fun isEnabled(settings: DrupalExtendSettings): Boolean =
        settings.isCodeStyleDocBlockSummaryLengthEnabled

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        if (docComment.isInlineVariableDocBlock()) return

        val allLines = docComment.drupalDocBlockLines()

        // `docBlockSummaryLines()` already handles @file by skipping the @file
        // tag line and returning what follows it as the summary region.
        val summaryLines = allLines.docBlockSummaryLines()

        // The first actual summary line (non-blank, non-tag, non-inheritdoc).
        val summaryLine = summaryLines.firstOrNull { it.isSummaryLine() } ?: return

        // Skip if the summary contains a URL.
        if (URL_PATTERN.containsMatchIn(summaryLine.normalizedTrimmedContent())) return

        // Measure the physical line length from the line start.
        val lineLength = summaryLine.rawText.length
        if (lineLength <= MAX_LENGTH) return

        context.registerDocBlockProblem(
            docComment,
            summaryLine,
            "DocBlock summary line must not exceed $MAX_LENGTH characters ($lineLength found)"
        )
    }
}
