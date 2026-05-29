package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal data class DrupalDocBlockLine(
    val rawText: String,
    val content: String,
    val lineStartOffset: Int,
    val contentStartOffset: Int,
    val lineEndOffset: Int,
) {
    val trimmedContent: String = content.trim()
    val isBlankContent: Boolean = trimmedContent.isBlank()
    val isTag: Boolean = trimmedContent.startsWith("@")

    fun contentEndOffset(): Int {
        return contentStartOffset + content.trimEnd().length
    }

    fun trimmedContentStartOffset(): Int {
        return contentStartOffset + content.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    }

    fun normalizedTrimmedContent(): String {
        return trimmedContent.removeSuffix("*/").trimEnd()
    }

    fun indentation(): String {
        return rawText.takeWhile { it == ' ' || it == '\t' }
    }

    fun normalizedBlankLineText(): String {
        return "${indentation()}*"
    }

    fun isOpeningLine(): Boolean {
        return rawText.trimStart().startsWith("/**")
    }

    fun isClosingLine(): Boolean {
        return rawText.trimStart().startsWith("*/")
    }
}

internal fun DrupalDocBlockLine.isSummaryLine(allowInheritDoc: Boolean = false): Boolean {
    if (isBlankContent || isTag) return false
    if (normalizedTrimmedContent().isBlank()) return false
    if (!allowInheritDoc && isInheritDocSummary()) return false

    return true
}

internal fun String.endsWithDocBlockSentencePunctuation(): Boolean {
    return lastOrNull() in setOf('.', '!', '?', ':')
}

internal fun DrupalDocBlockLine.isInheritDocSummary(): Boolean {
    return normalizedTrimmedContent() in setOf("{@inheritdoc}", "{@inheritDoc}")
}

internal fun DrupalDocBlockLine.tagName(): String? {
    if (!isTag) return null
    return Regex("""^@([A-Za-z_][\w\\-]*)""").find(trimmedContent)?.groupValues?.get(1)
}

internal fun DrupalDocBlockLine.tagGroup(): String? {
    return when (val tagName = tagName()) {
        "param" -> "param"
        "return" -> "return"
        "throws", "throw" -> "throws"
        null -> null
        else -> tagName
    }
}

internal fun PhpDocComment.drupalDocBlockLines(): List<DrupalDocBlockLine> {
    val text = text
    val lines = mutableListOf<DrupalDocBlockLine>()
    var lineStartOffset = 0

    while (lineStartOffset <= text.length) {
        val lineEndOffset = text.indexOfLineBreak(lineStartOffset)
            ?: text.length
        val rawLine = text.substring(lineStartOffset, lineEndOffset)
        val contentStartInLine = rawLine.contentStartInLine()

        lines += DrupalDocBlockLine(
            rawText = rawLine,
            content = rawLine.substring(contentStartInLine),
            lineStartOffset = lineStartOffset,
            contentStartOffset = lineStartOffset + contentStartInLine,
            lineEndOffset = lineEndOffset,
        )

        if (lineEndOffset == text.length) break
        lineStartOffset = text.nextLineStart(lineEndOffset)
    }

    return lines
}

private fun String.indexOfLineBreak(startOffset: Int): Int? {
    val lineFeed = indexOf('\n', startOffset).takeIf { it >= 0 }
    val carriageReturn = indexOf('\r', startOffset).takeIf { it >= 0 }

    return listOfNotNull(lineFeed, carriageReturn).minOrNull()
}

private fun String.nextLineStart(lineEndOffset: Int): Int {
    if (getOrNull(lineEndOffset) == '\r' && getOrNull(lineEndOffset + 1) == '\n') {
        return lineEndOffset + 2
    }

    return lineEndOffset + 1
}

private fun String.contentStartInLine(): Int {
    val marker = Regex("""^\s*(?:/\*\*|\*/|\*)\s?""")
        .find(this)
        ?: return 0

    return marker.range.last + 1
}
