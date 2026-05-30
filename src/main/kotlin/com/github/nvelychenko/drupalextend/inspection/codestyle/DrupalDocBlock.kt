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

    fun normalizedContent(): String {
        return content.removeSuffix("*/").trimEnd()
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
        "deprecated", "see" -> "deprecated_see"
        null -> null
        else -> tagName
    }
}

internal data class DrupalDocBlockParamTag(
    val type: String,
    val typeEndOffset: Int,
    val name: String?,
    val nameStartOffset: Int?,
    val nameEndOffset: Int?,
    val description: String?,
    val descriptionStartOffset: Int?,
)

internal data class DrupalDocBlockReturnTag(
    val type: String,
    val typeStartOffset: Int,
    val typeEndOffset: Int,
    val description: String?,
    val descriptionStartOffset: Int?,
)

internal data class DrupalDocBlockThrowsTag(
    val type: String,
    val typeStartOffset: Int,
    val typeEndOffset: Int,
    val description: String?,
    val descriptionStartOffset: Int?,
)

internal data class DrupalDocBlockSeeTag(
    val reference: String?,
    val referenceStartOffset: Int?,
    val referenceEndOffset: Int?,
    val extraText: String?,
    val extraTextStartOffset: Int?,
)

internal fun DrupalDocBlockLine.paramTag(): DrupalDocBlockParamTag? {
    return trimmedContent.drupalDocBlockParamTag()
}

internal fun DrupalDocBlockLine.returnTag(): DrupalDocBlockReturnTag? {
    return trimmedContent.drupalDocBlockReturnTag()
}

internal fun DrupalDocBlockLine.throwsTag(): DrupalDocBlockThrowsTag? {
    return trimmedContent.drupalDocBlockThrowsTag()
}

internal fun DrupalDocBlockLine.seeTag(): DrupalDocBlockSeeTag? {
    return trimmedContent.drupalDocBlockSeeTag()
}

internal fun List<DrupalDocBlockLine>.tagDescriptionLines(tagIndex: Int): List<DrupalDocBlockLine> {
    val tagLine = getOrNull(tagIndex)?.takeIf { it.tagName() in DRUPAL_DOCBLOCK_DESCRIPTION_TAGS } ?: return emptyList()
    val continuationLines = tagContinuationLines(tagIndex)
    val descriptionStartIndex = continuationDescriptionStartIndex(tagLine, continuationLines)

    return continuationLines
        .drop(descriptionStartIndex)
        .filter { !it.isBlankContent && it.normalizedTrimmedContent().isNotBlank() }
}

internal fun List<DrupalDocBlockLine>.tagContinuationLines(tagIndex: Int): List<DrupalDocBlockLine> {
    return drop(tagIndex + 1)
        .takeWhile { !it.isTag && !it.isBlankContent }
}

internal fun String.drupalDocBlockParamTag(): DrupalDocBlockParamTag? {
    val tagMatch = Regex("""^@param(?:\s+|$)""").find(this) ?: return null
    val typeStart = tagMatch.range.last + 1
    if (typeStart >= length) return null

    val tokens = topLevelDocBlockTokens(typeStart)
    if (tokens.isEmpty()) return null

    val nameTokenIndex = tokens.indexOfFirst { substring(it).isParamNameToken() }
    val typeEndOffset = when {
        nameTokenIndex > 0 -> tokens[nameTokenIndex - 1].endInclusive + 1
        nameTokenIndex == 0 -> tokens[nameTokenIndex].start
        else -> tokens.first().endInclusive + 1
    }
    val type = substring(typeStart, typeEndOffset).trim()
    val nameRange = tokens.getOrNull(nameTokenIndex)
    val descriptionStart = nameRange
        ?.endInclusive
        ?.plus(1)
        ?.let { skipWhitespace(it) }
        ?.takeIf { it < length }
    val description = descriptionStart?.let { substring(it).trim() }?.takeIf { it.isNotBlank() }

    return DrupalDocBlockParamTag(
        type = type,
        typeEndOffset = typeEndOffset,
        name = nameRange?.let { substring(it) },
        nameStartOffset = nameRange?.start,
        nameEndOffset = nameRange?.endInclusive?.plus(1),
        description = description,
        descriptionStartOffset = descriptionStart,
    )
}

internal fun String.drupalDocBlockReturnTag(): DrupalDocBlockReturnTag? {
    val tag = drupalDocBlockTypedTag("return") ?: return null

    return DrupalDocBlockReturnTag(
        type = tag.type,
        typeStartOffset = tag.typeStartOffset,
        typeEndOffset = tag.typeEndOffset,
        description = tag.description,
        descriptionStartOffset = tag.descriptionStartOffset,
    )
}

internal fun String.drupalDocBlockThrowsTag(): DrupalDocBlockThrowsTag? {
    val tag = drupalDocBlockTypedTag("throws") ?: return null

    return DrupalDocBlockThrowsTag(
        type = tag.type,
        typeStartOffset = tag.typeStartOffset,
        typeEndOffset = tag.typeEndOffset,
        description = tag.description,
        descriptionStartOffset = tag.descriptionStartOffset,
    )
}

internal fun String.drupalDocBlockSeeTag(): DrupalDocBlockSeeTag? {
    val tagMatch = Regex("""^@see(?:\s+|$)""").find(this) ?: return null
    val referenceStart = (tagMatch.range.last + 1)
        .let { skipWhitespace(it) }
        .takeIf { it < length }
    val referenceRange = referenceStart?.let { start ->
        val end = indexOfFirstAfter(start) { it.isWhitespace() } ?: length
        start until end
    }
    val extraTextStart = referenceRange
        ?.endInclusive
        ?.plus(1)
        ?.let { skipWhitespace(it) }
        ?.takeIf { it < length }

    return DrupalDocBlockSeeTag(
        reference = referenceRange?.let { substring(it) },
        referenceStartOffset = referenceRange?.start,
        referenceEndOffset = referenceRange?.endInclusive?.plus(1),
        extraText = extraTextStart?.let { substring(it).trim() }?.takeIf { it.isNotBlank() },
        extraTextStartOffset = extraTextStart,
    )
}

internal fun PhpDocComment.isInlineVariableDocBlock(): Boolean {
    val contentLines = drupalDocBlockLines()
        .mapNotNull { line ->
            when {
                line.isOpeningLine() -> line.normalizedTrimmedContent().takeIf { it.isNotBlank() }
                line.isClosingLine() -> null
                line.isBlankContent -> null
                else -> line.normalizedTrimmedContent()
            }
        }
    if (contentLines.size != 1) return false

    return contentLines.single().docBlockTagName() in setOf("var", "phpstan-var", "psalm-var")
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

internal fun String.docBlockTypeNestingBalance(): Int {
    var balance = 0
    forEach { char ->
        when (char) {
            '<', '{', '[', '(' -> balance++
            '>', '}', ']', ')' -> if (balance > 0) balance--
        }
    }

    return balance
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

private fun String.topLevelDocBlockTokens(startOffset: Int): List<IntRange> {
    val tokens = mutableListOf<IntRange>()
    var tokenStart: Int? = null
    var nestingDepth = 0
    var offset = startOffset

    while (offset < length) {
        val char = this[offset]
        when {
            char in "<{[(" -> {
                tokenStart = tokenStart ?: offset
                nestingDepth++
            }

            char in ">}])" -> {
                tokenStart = tokenStart ?: offset
                if (nestingDepth > 0) nestingDepth--
            }

            char.isWhitespace() && nestingDepth == 0 -> {
                tokenStart?.let { tokens += it until offset }
                tokenStart = null
            }

            else -> {
                tokenStart = tokenStart ?: offset
            }
        }
        offset++
    }

    tokenStart?.let { tokens += it until length }
    return tokens
}

private fun continuationDescriptionStartIndex(
    tagLine: DrupalDocBlockLine,
    continuationLines: List<DrupalDocBlockLine>,
): Int {
    var typeBalance = tagLine.tagTypeNestingBalance()
    if (typeBalance <= 0) return 0

    continuationLines.forEachIndexed { index, line ->
        typeBalance -= line.normalizedTrimmedContent().docBlockTypeClosingCount(typeBalance)
        typeBalance += line.normalizedTrimmedContent().docBlockTypeOpeningCount()
        if (typeBalance <= 0) return index + 1
    }

    return continuationLines.size
}

private fun DrupalDocBlockLine.tagTypeNestingBalance(): Int {
    return when (tagName()) {
        "param" -> paramTag()?.type?.docBlockTypeNestingBalance()
        "return" -> returnTag()?.type?.docBlockTypeNestingBalance()
        "throws" -> throwsTag()?.type?.docBlockTypeNestingBalance()
        else -> null
    } ?: 0
}

internal fun String.docBlockTypeOpeningCount(): Int {
    return count { it in "<{[(" }
}

internal fun String.docBlockTypeClosingCount(maxCount: Int): Int {
    if (maxCount <= 0) return 0

    var count = 0
    for (char in this) {
        if (char !in ">}])") continue
        count++
        if (count >= maxCount) return count
    }

    return count
}

private fun String.skipWhitespace(startOffset: Int): Int {
    var offset = startOffset
    while (offset < length && this[offset].isWhitespace()) {
        offset++
    }

    return offset
}

private data class DrupalDocBlockTypedTag(
    val type: String,
    val typeStartOffset: Int,
    val typeEndOffset: Int,
    val description: String?,
    val descriptionStartOffset: Int?,
)

private fun String.drupalDocBlockTypedTag(tagName: String): DrupalDocBlockTypedTag? {
    val tagMatch = Regex("""^@$tagName(?:\s+|$)""").find(this) ?: return null
    val typeStart = tagMatch.range.last + 1
    if (typeStart >= length) return null

    val tokens = topLevelDocBlockTokens(typeStart)
    val typeRange = tokens.firstOrNull() ?: return null
    val typeEndOffset = typeRange.endInclusive + 1
    val descriptionStart = tokens
        .getOrNull(1)
        ?.start
    val description = descriptionStart?.let { substring(it).trim() }?.takeIf { it.isNotBlank() }

    return DrupalDocBlockTypedTag(
        type = substring(typeStart, typeEndOffset).trim(),
        typeStartOffset = typeStart,
        typeEndOffset = typeEndOffset,
        description = description,
        descriptionStartOffset = descriptionStart,
    )
}

private fun String.indexOfFirstAfter(startOffset: Int, predicate: (Char) -> Boolean): Int? {
    for (index in startOffset until length) {
        if (predicate(this[index])) return index
    }

    return null
}

private fun String.isParamNameToken(): Boolean {
    return startsWith("\$") || startsWith("...\$") || startsWith("&\$") || startsWith("&...\$")
}

private fun String.docBlockTagName(): String? {
    return Regex("""^@([A-Za-z_][\w\\-]*)""").find(this)?.groupValues?.get(1)
}

internal val DRUPAL_DOCBLOCK_DESCRIPTION_TAGS = setOf("param", "return", "throws")
