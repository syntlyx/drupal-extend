package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.Function

internal object DrupalDocBlockFixes {
    val fixDocBlock = DrupalCodeStyleTextEditQuickFix("Fix Drupal DocBlock") { project, descriptor ->
        val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
        docComment.fullDocBlockFixEdit(project.drupalExtendSettings)
            ?.let { listOf(it) }
            ?: emptyList()
    }
}

internal fun PhpDocComment.fullDocBlockFixEdit(
    settings: DrupalExtendSettings,
): TextEdit? {
    if (isInlineVariableDocBlock()) return null

    val fixedText = fullDocBlockFixText(settings) ?: return null
    if (fixedText == text) return null

    return TextEdit(textRange.startOffset, textRange.endOffset, fixedText)
}

private fun PhpDocComment.fullDocBlockFixText(
    settings: DrupalExtendSettings,
): String? {
    val lines = drupalDocBlockLines()
    if (lines.isEmpty()) return null

    val indentation = documentIndentation()
    val lineSeparator = document()?.lineSeparator() ?: "\n"
    val contentLines = normalizedContentLines()
    if (contentLines.isEmpty()) return null

    val function = owningFunction()
    val normalizedContent = contentLines
        .withMissingParamNames(function, settings)
        .withThrowsExceptionNamespaces(settings)
        .withInlineDescriptionsOnContinuationLines(settings)
        .withDescriptionIndentation(settings)
        .withTagDescriptionPunctuation(settings)
        .withSummaryPunctuation(settings)
        .withSemanticBlankLines(settings)

    return buildString {
        append("/**")
        normalizedContent.forEach { line ->
            append(lineSeparator)
            append(indentation).append(" *")
            if (line.isNotBlank()) {
                append(' ').append(line)
            }
        }
        append(lineSeparator).append(indentation).append(" */")
    }
}

private fun PhpDocComment.documentIndentation(): String {
    val fileText = containingFile?.text
    if (fileText != null) {
        val lineStart = fileText.lastIndexOf('\n', (textRange.startOffset - 1).coerceAtLeast(0))
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        return fileText.substring(lineStart, textRange.startOffset)
            .takeWhile { it == ' ' || it == '\t' }
    }

    val document = document() ?: return drupalDocBlockLines().firstOrNull()?.indentation().orEmpty()
    return document.lineText(document.getLineNumber(textRange.startOffset))
        .takeWhile { it == ' ' || it == '\t' }
}

private fun PhpDocComment.owningFunction(): Function? {
    val file = containingFile ?: return null
    return PsiTreeUtil.findChildrenOfType(file, Function::class.java)
        .firstOrNull { it.docComment?.textRange == textRange }
}

private fun PhpDocComment.normalizedContentLines(): List<String> {
    return drupalDocBlockLines()
        .mapNotNull { line ->
            when {
                line.isOpeningLine() -> line.normalizedTrimmedContent().takeIf { it.isNotBlank() }
                line.isClosingLine() -> null
                line.isBlankContent -> null
                else -> line.normalizedContent()
            }
        }
}

private fun List<String>.withMissingParamNames(
    function: Function?,
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockMissingParamNameEnabled || function == null) return this

    var paramIndex = 0
    return map { line ->
        if (line.tagName() != "param") return@map line

        val parameterName = function.parameters.getOrNull(paramIndex)?.phpDocVariableName()
        paramIndex++
        parameterName?.let { line.withParamName(it) } ?: line
    }
}

private fun List<String>.withInlineDescriptionsOnContinuationLines(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockTagDescriptionLineEnabled) return this

    return flatMap { line ->
        line.splitInlineDescription() ?: listOf(line)
    }
}

private fun List<String>.withThrowsExceptionNamespaces(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockApiTagsEnabled) return this

    return map { line ->
        if (line.tagName() != "throws") return@map line
        val tag = line.drupalDocBlockThrowsTag() ?: return@map line
        if (tag.type.startsWith("\\")) return@map line

        line.substring(0, tag.typeStartOffset) + "\\" + line.substring(tag.typeStartOffset)
    }
}

private fun List<String>.withDescriptionIndentation(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockTagDescriptionIndentationEnabled) return this

    return toMutableList().also { lines ->
        lines.indices.forEach { index ->
            val tagName = lines[index].tagName()
                ?: return@forEach
            if (tagName !in DRUPAL_DOCBLOCK_DESCRIPTION_TAGS) return@forEach

            var previousDescriptionLine: String? = null
            lines.tagDescriptionIndexes(index, tagName).forEach { descriptionIndex ->
                val normalizedLine = lines[descriptionIndex].normalizedDescriptionLine(previousDescriptionLine)
                lines[descriptionIndex] = normalizedLine
                previousDescriptionLine = normalizedLine
            }
        }
    }
}

private fun List<String>.withSummaryPunctuation(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockSummaryPunctuationEnabled) return this

    val summaryIndex = takeWhile { !it.isTag() }
        .indexOfLast { it.isSummaryLineContent() }
    if (summaryIndex < 0) return this
    val summary = get(summaryIndex)
    if (summary.endsWithDocBlockSentencePunctuation()) return this

    return toMutableList().also {
        it[summaryIndex] = "$summary."
    }
}

private fun List<String>.withTagDescriptionPunctuation(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockTagDescriptionPunctuationEnabled) return this

    return toMutableList().also { lines ->
        lines.indices.forEach { index ->
            val tagName = lines[index].tagName()
                ?: return@forEach
            if (tagName !in DRUPAL_DOCBLOCK_DESCRIPTION_TAGS) return@forEach

            val descriptionIndex = lines.tagDescriptionIndex(index, tagName) ?: return@forEach
            if (lines[descriptionIndex].trimEnd().endsWithDocBlockSentencePunctuation()) return@forEach

            lines[descriptionIndex] = lines[descriptionIndex].trimEnd() + "."
        }
    }
}

private fun List<String>.withSemanticBlankLines(
    settings: DrupalExtendSettings,
): List<String> {
    val result = mutableListOf<String>()
    var previousEntry: DocBlockContentEntry? = null

    forEach { line ->
        val entry = line.toContentEntry(previousEntry?.tagGroup)
        val needsBlankLineBeforeTags = settings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled &&
            previousEntry?.tagGroup == null &&
            previousEntry?.content?.isNotBlank() == true &&
            entry.tagGroup != null
        val needsBlankLineBetweenTagGroups = settings.isCodeStyleDocBlockTagGroupSpacingEnabled &&
            previousEntry?.tagGroup != null &&
            entry.tagGroup != null &&
            previousEntry.tagGroup != entry.tagGroup

        if ((needsBlankLineBeforeTags || needsBlankLineBetweenTagGroups) && result.lastOrNull()?.isNotBlank() == true) {
            result += ""
        }

        result += entry.content
        previousEntry = entry
    }

    return if (settings.isCodeStyleDocBlockBlankLineNormalizationEnabled) {
        result.withoutRedundantBlankLines()
    } else {
        result
    }
}

private fun List<String>.withoutRedundantBlankLines(): List<String> {
    val result = mutableListOf<String>()
    forEachIndexed { index, line ->
        if (line.isNotBlank()) {
            result += line
            return@forEachIndexed
        }

        val previous = result.lastOrNull()
        val next = drop(index + 1).firstOrNull { it.isNotBlank() }
        if (previous.isNullOrBlank() || next == null) return@forEachIndexed

        result += ""
    }

    return result
}

private data class DocBlockContentEntry(
    val content: String,
    val tagGroup: String?,
)

private fun String.toContentEntry(previousTagGroup: String?): DocBlockContentEntry {
    val tagGroup = tagGroup()
    return DocBlockContentEntry(
        content = trimEnd(),
        tagGroup = tagGroup ?: previousTagGroup?.takeIf { !isTag() }
    )
}

private fun String.withParamName(parameterName: String): String {
    val tag = drupalDocBlockParamTag() ?: return this
    if (tag.name?.isParamName() == true) return this

    return substring(0, tag.typeEndOffset) + " $parameterName" + substring(tag.typeEndOffset)
}

private fun String.splitInlineDescription(): List<String>? {
    if (tagName() == "param") {
        val tag = drupalDocBlockParamTag() ?: return null
        val nameEndOffset = tag.nameEndOffset ?: return null
        val description = tag.description?.trim() ?: return null
        if (description.isBlank()) return null

        return listOf(substring(0, nameEndOffset).trimEnd(), "  $description")
    }

    if (tagName() == "return") {
        val tag = drupalDocBlockReturnTag() ?: return null
        val description = tag.description?.trim() ?: return null
        if (description.isBlank()) return null

        return listOf(substring(0, tag.typeEndOffset).trimEnd(), "  $description")
    }

    if (tagName() == "throws") {
        val tag = drupalDocBlockThrowsTag() ?: return null
        val description = tag.description?.trim() ?: return null
        if (description.isBlank()) return null

        return listOf(substring(0, tag.typeEndOffset).trimEnd(), "  $description")
    }

    return null
}

private fun String.tagName(): String? {
    return Regex("""^@([A-Za-z_][\w\\-]*)""").find(this)?.groupValues?.get(1)
}

private fun String.tagGroup(): String? {
    return when (val tagName = tagName()) {
        "param" -> "param"
        "return" -> "return"
        "throws", "throw" -> "throws"
        "deprecated", "see" -> "deprecated_see"
        null -> null
        else -> tagName
    }
}

private fun String.isTag(): Boolean {
    return tagName() != null
}

private fun String.isSummaryLineContent(): Boolean {
    if (isBlank() || isTag()) return false
    return trim() !in setOf("{@inheritdoc}", "{@inheritDoc}")
}

private fun String.isParamName(): Boolean {
    return startsWith("\$") || startsWith("...\$") || startsWith("&\$")
}

private fun MutableList<String>.tagDescriptionIndex(tagIndex: Int, tagName: String): Int? {
    val continuationDescriptionIndexes = tagDescriptionIndexes(tagIndex, tagName)

    if (continuationDescriptionIndexes.isNotEmpty()) {
        return continuationDescriptionIndexes.last()
    }

    return tagIndex.takeIf { this[it].hasInlineTagDescription(tagName) }
}

private fun List<String>.tagDescriptionIndexes(tagIndex: Int, tagName: String): List<Int> {
    val tagLine = getOrNull(tagIndex)?.takeIf { it.tagName() == tagName } ?: return emptyList()
    val continuationIndexes = ((tagIndex + 1) until size)
        .takeWhile { !this[it].isTag() && this[it].isNotBlank() }
    val descriptionStartIndex = tagLine.continuationDescriptionStartIndex(
        continuationIndexes.map { this[it] }
    )

    return continuationIndexes
        .drop(descriptionStartIndex)
        .filter { this[it].trim().isNotBlank() }
}

private fun String.hasInlineTagDescription(tagName: String): Boolean {
    return when (tagName) {
        "param" -> drupalDocBlockParamTag()?.description?.isNotBlank() == true
        "return" -> drupalDocBlockReturnTag()?.description?.isNotBlank() == true
        "throws" -> drupalDocBlockThrowsTag()?.description?.isNotBlank() == true
        else -> false
    }
}

private fun com.jetbrains.php.lang.psi.elements.Parameter.phpDocVariableName(): String {
    return "\$" + name.removePrefix("\$")
}

private fun String.normalizedDescriptionLine(previousDescriptionLine: String?): String {
    return expectedDescriptionIndentation(previousDescriptionLine) + trim()
}

private fun String.expectedDescriptionIndentation(previousDescriptionLine: String?): String {
    val currentIndent = descriptionIndentationWidth()
    val previousIndent = previousDescriptionLine?.descriptionIndentationWidth() ?: DESCRIPTION_INDENT.length
    val trimmed = trim()

    if (trimmed.startsWith("- ") && previousDescriptionLine?.trimEnd()?.endsWith(":") == true) {
        return " ".repeat(previousIndent + DESCRIPTION_INDENT.length)
    }

    if (trimmed.startsWith("- ") && previousDescriptionLine?.trimStart()?.startsWith("- ") == true) {
        return " ".repeat(previousIndent)
    }

    if (trimmed.startsWith("- ") && currentIndent >= DESCRIPTION_INDENT.length && currentIndent % DESCRIPTION_INDENT.length == 0) {
        return " ".repeat(currentIndent)
    }

    return DESCRIPTION_INDENT
}

private fun String.descriptionIndentationWidth(): Int {
    return takeWhile { it == ' ' }.length
}

private fun String.continuationDescriptionStartIndex(continuationLines: List<String>): Int {
    var typeBalance = tagTypeNestingBalance()
    if (typeBalance <= 0) return 0

    continuationLines.forEachIndexed { index, line ->
        typeBalance -= line.trim().docBlockTypeClosingCount(typeBalance)
        typeBalance += line.trim().docBlockTypeOpeningCount()
        if (typeBalance <= 0) return index + 1
    }

    return continuationLines.size
}

private fun String.tagTypeNestingBalance(): Int {
    return when (tagName()) {
        "param" -> drupalDocBlockParamTag()?.type?.docBlockTypeNestingBalance()
        "return" -> drupalDocBlockReturnTag()?.type?.docBlockTypeNestingBalance()
        "throws" -> drupalDocBlockThrowsTag()?.type?.docBlockTypeNestingBalance()
        else -> null
    } ?: 0
}

private const val DESCRIPTION_INDENT = "  "
