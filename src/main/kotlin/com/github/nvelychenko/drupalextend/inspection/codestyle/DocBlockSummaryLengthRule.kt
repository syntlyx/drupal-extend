package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
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
 * The quick fix wraps the overflow at word boundaries onto continuation lines — it cannot
 * shorten prose, but it brings the line under the limit without losing a word. Lines whose
 * first word alone exceeds the limit get no fix.
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
            "DocBlock summary line must not exceed $MAX_LENGTH characters ($lineLength found)",
            wrapSummaryFix()
        )
    }

    private fun wrapSummaryFix(): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Wrap summary at $MAX_LENGTH characters") { _, descriptor ->
            (descriptor.psiElement as? PhpDocComment)
                ?.summaryWrapEdit()
                ?.let { listOf(it) }
                ?: emptyList()
        }
}

/**
 * Word-wraps an over-long summary line onto continuation lines with the same `* ` prefix.
 * Returns null when nothing needs wrapping, when no break point exists, or when the summary
 * shares its line with the docblock markers (one-line docblocks are normalized by other rules).
 */
internal fun PhpDocComment.summaryWrapEdit(): TextEdit? {
    if (isInlineVariableDocBlock()) return null

    val summaryLine = drupalDocBlockLines()
        .docBlockSummaryLines()
        .firstOrNull { it.isSummaryLine() }
        ?: return null
    if (Regex("""https?://""").containsMatchIn(summaryLine.normalizedTrimmedContent())) return null
    if (summaryLine.rawText.length <= 80) return null
    if (summaryLine.rawText.contains("*/") || summaryLine.rawText.contains("/**")) return null

    // The literal line prefix up to the content (indentation + `* `), reused for new lines.
    val prefix = summaryLine.rawText.substring(0, summaryLine.contentStartOffset - summaryLine.lineStartOffset)
    val words = summaryLine.trimmedContent.split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (words.isEmpty()) return null

    val wrappedLines = mutableListOf(StringBuilder())
    for (word in words) {
        val current = wrappedLines.last()
        val projected = prefix.length + current.length + (if (current.isEmpty()) 0 else 1) + word.length
        if (current.isNotEmpty() && projected > 80) {
            wrappedLines.add(StringBuilder(word))
        } else {
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
    }
    // A single word longer than the limit can't be wrapped.
    if (wrappedLines.size < 2) return null

    val docStart = textRange.startOffset
    val replacement = wrappedLines.joinToString("\n$prefix") { it.toString() }

    return TextEdit(
        docStart + summaryLine.contentStartOffset,
        docStart + summaryLine.contentEndOffset(),
        replacement
    )
}
