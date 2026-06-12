package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.PhpFile

internal object FileWhitespaceRule : DrupalCodeStyleRule {
    override val id: String = "file_whitespace"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleFileWhitespaceEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val file = element as? PhpFile ?: return
        val document = file.document() ?: return

        file.fileWhitespaceIssues(document).forEach { issue ->
            context.register(
                DrupalCodeStyleProblem(
                    element = file,
                    message = issue.message,
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    rangeInElement = file.relativeRange(issue.rangeStartOffset, issue.rangeEndOffset),
                    fixes = listOf(normalizeFileWhitespaceFix())
                )
            )
        }
    }

    private fun normalizeFileWhitespaceFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize file whitespace") { _, descriptor ->
            val file = descriptor.psiElement.containingFile ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = file.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            file.fileWhitespaceEdits(document)
        }
    }
}

internal fun PsiFile.fileWhitespaceEdits(document: Document): List<TextEdit> {
    return fileWhitespaceIssues(document)
        .map { it.edit }
        .distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
}

private fun PsiFile.fileWhitespaceIssues(document: Document): List<FileWhitespaceIssue> {
    val eofEdit = document.endOfFileWhitespaceEdit()
    val issues = document.trailingWhitespaceIssues(eofEdit).toMutableList()
    eofEdit?.let {
        issues += FileWhitespaceIssue(
            message = "Files must end with exactly one newline",
            edit = it,
            rangeStartOffset = it.issueStartOffset(),
            rangeEndOffset = it.issueEndOffset(),
        )
    }

    return issues
}

private data class FileWhitespaceIssue(
    val message: String,
    val edit: TextEdit,
    val rangeStartOffset: Int,
    val rangeEndOffset: Int,
)

private fun Document.trailingWhitespaceIssues(eofEdit: TextEdit?): List<FileWhitespaceIssue> {
    val issues = mutableListOf<FileWhitespaceIssue>()
    for (line in 0 until lineCount) {
        val lineStartOffset = getLineStartOffset(line)
        val lineEndOffset = getLineEndOffset(line)
        val trailingWhitespaceStartOffset = trailingWhitespaceStartOffset(lineStartOffset, lineEndOffset)
        if (trailingWhitespaceStartOffset == lineEndOffset) continue

        val edit = TextEdit(trailingWhitespaceStartOffset, lineEndOffset, "")
        if (eofEdit != null && edit.overlaps(eofEdit)) continue
        issues += FileWhitespaceIssue(
            message = "Lines must not have trailing whitespace",
            edit = edit,
            rangeStartOffset = edit.startOffset,
            rangeEndOffset = edit.endOffset,
        )
    }

    return issues
}

private fun Document.trailingWhitespaceStartOffset(lineStartOffset: Int, lineEndOffset: Int): Int {
    var offset = lineEndOffset
    while (offset > lineStartOffset && charsSequence[offset - 1].isHorizontalWhitespace()) {
        offset--
    }

    return offset
}

private fun Document.endOfFileWhitespaceEdit(): TextEdit? {
    val text = charsSequence
    if (text.isEmpty()) return null

    val contentEndOffset = text.lastNonWhitespaceOffset()?.plus(1) ?: return null
    val expectedSuffix = lineSeparator()
    val currentSuffix = text.subSequence(contentEndOffset, text.length).toString()
    if (currentSuffix == expectedSuffix) return null

    return TextEdit(contentEndOffset, text.length, expectedSuffix)
}

private fun TextEdit.issueStartOffset(): Int {
    if (startOffset < endOffset) return startOffset
    return (startOffset - 1).coerceAtLeast(0)
}

private fun TextEdit.issueEndOffset(): Int {
    if (startOffset < endOffset) return endOffset
    return startOffset
}

private fun CharSequence.lastNonWhitespaceOffset(): Int? {
    for (offset in length - 1 downTo 0) {
        if (!this[offset].isWhitespace()) return offset
    }

    return null
}

private fun Char.isHorizontalWhitespace(): Boolean {
    return this == ' ' || this == '\t'
}
