package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal fun PsiElement.document(): Document? {
    val file = containingFile ?: return null
    return PsiDocumentManager.getInstance(project).getDocument(file)
}

internal fun Document.lineText(line: Int): String {
    return charsSequence
        .subSequence(getLineStartOffset(line), getLineEndOffset(line))
        .toString()
}

internal fun Document.lineNumber(element: PsiElement): Int {
    return getLineNumber(element.textRange.startOffset)
}

internal fun Document.endLineNumber(element: PsiElement): Int {
    return getLineNumber((element.textRange.endOffset - 1).coerceAtLeast(element.textRange.startOffset))
}

internal fun Document.blankLinesBefore(line: Int): Int {
    var currentLine = line - 1
    var blankLines = 0
    while (currentLine >= 0 && lineText(currentLine).isBlank()) {
        blankLines++
        currentLine--
    }
    return blankLines
}

internal fun Document.blankLinesAfter(line: Int): Int {
    var currentLine = line + 1
    var blankLines = 0
    while (currentLine < lineCount && lineText(currentLine).isBlank()) {
        blankLines++
        currentLine++
    }
    return blankLines
}

internal fun Document.previousNonBlankLineNumber(line: Int): Int? {
    var currentLine = line - 1
    while (currentLine >= 0) {
        if (lineText(currentLine).isNotBlank()) return currentLine
        currentLine--
    }
    return null
}

internal fun Document.nextNonBlankLineNumber(line: Int): Int? {
    var currentLine = line + 1
    while (currentLine < lineCount) {
        if (lineText(currentLine).isNotBlank()) return currentLine
        currentLine++
    }
    return null
}

internal fun Document.previousNonBlankLine(line: Int): String? {
    return previousNonBlankLineNumber(line)?.let { lineText(it) }
}

internal fun Document.nextNonBlankLine(line: Int): String? {
    return nextNonBlankLineNumber(line)?.let { lineText(it) }
}

internal fun Document.lineSeparator(): String {
    val text = charsSequence
    var index = 0
    while (index < text.length) {
        if (text[index] == '\r') {
            return if (index + 1 < text.length && text[index + 1] == '\n') "\r\n" else "\r"
        }
        if (text[index] == '\n') return "\n"
        index++
    }
    return "\n"
}

internal fun Document.blankLineRangeBefore(line: Int): TextRange? {
    val previousLine = previousNonBlankLineNumber(line) ?: return null
    return TextRange(getLineEndOffset(previousLine), getLineStartOffset(line))
}

internal fun Document.blankLineRangeAfter(line: Int): TextRange? {
    val nextLine = nextNonBlankLineNumber(line) ?: return null
    return TextRange(getLineEndOffset(line), getLineStartOffset(nextLine))
}

internal fun Document.normalizeBlankLinesBeforeEdit(line: Int): TextEdit? {
    val range = blankLineRangeBefore(line) ?: return null
    return TextEdit(range.startOffset, range.endOffset, lineSeparator() + lineSeparator())
}

internal fun Document.normalizeBlankLinesAfterEdit(line: Int): TextEdit? {
    val range = blankLineRangeAfter(line) ?: return null
    return TextEdit(range.startOffset, range.endOffset, lineSeparator() + lineSeparator())
}

internal fun Document.removeBlankLinesBeforeEdit(line: Int): TextEdit? {
    if (blankLinesBefore(line) == 0) return null
    val range = blankLineRangeBefore(line) ?: return null
    return TextEdit(range.startOffset, range.endOffset, lineSeparator())
}

internal fun Document.removeBlankLinesAfterEdit(line: Int): TextEdit? {
    if (blankLinesAfter(line) == 0) return null
    val range = blankLineRangeAfter(line) ?: return null
    return TextEdit(range.startOffset, range.endOffset, lineSeparator())
}
