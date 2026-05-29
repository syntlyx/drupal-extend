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
                else -> line.normalizedTrimmedContent()
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

private fun List<String>.withDescriptionIndentation(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockTagDescriptionIndentationEnabled) return this

    var activeDescriptionTag: String? = null
    return map { line ->
        val tagName = line.tagName()
        when {
            tagName == "param" || tagName == "return" -> {
                activeDescriptionTag = tagName
                line
            }

            tagName != null -> {
                activeDescriptionTag = null
                line
            }

            activeDescriptionTag != null -> "  ${line.trim()}"
            else -> line
        }
    }
}

private fun List<String>.withSummaryPunctuation(
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockSummaryPunctuationEnabled) return this

    val summaryIndex = takeWhile { !it.isTag() }
        .indexOfFirst { it.isSummaryLineContent() }
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
            if (tagName != "param" && tagName != "return") return@forEach

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
        tagGroup = tagGroup ?: previousTagGroup?.takeIf { !isTag() && startsWith("  ") }
    )
}

private fun String.withParamName(parameterName: String): String {
    val match = PARAM_TAG.find(this) ?: return this
    val nameToken = match.groups[2]?.value
    if (nameToken?.isParamName() == true) return this

    val typeEndOffset = match.groups[1]?.range?.last?.plus(1) ?: return this
    return substring(0, typeEndOffset) + " $parameterName" + substring(typeEndOffset)
}

private fun String.splitInlineDescription(): List<String>? {
    val match = when (tagName()) {
        "param" -> PARAM_WITH_INLINE_DESCRIPTION.find(this)
        "return" -> RETURN_WITH_INLINE_DESCRIPTION.find(this)
        else -> null
    } ?: return null

    val tag = match.groups["tag"]?.value?.trimEnd() ?: return null
    val description = match.groups["description"]?.value?.trim() ?: return null
    if (description.isBlank()) return null

    return listOf(tag, "  $description")
}

private fun String.tagName(): String? {
    return Regex("""^@([A-Za-z_][\w\\-]*)""").find(this)?.groupValues?.get(1)
}

private fun String.tagGroup(): String? {
    return when (val tagName = tagName()) {
        "param" -> "param"
        "return" -> "return"
        "throws", "throw" -> "throws"
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
    val continuationDescriptionIndexes = ((tagIndex + 1) until size)
        .takeWhile { !this[it].isTag() && this[it].isNotBlank() }
        .filter { this[it].trim().isNotBlank() }

    if (continuationDescriptionIndexes.isNotEmpty()) {
        return continuationDescriptionIndexes.last()
    }

    return tagIndex.takeIf { this[it].hasInlineTagDescription(tagName) }
}

private fun String.hasInlineTagDescription(tagName: String): Boolean {
    return when (tagName) {
        "param" -> PARAM_WITH_INLINE_DESCRIPTION.matches(this)
        "return" -> RETURN_WITH_INLINE_DESCRIPTION.matches(this)
        else -> false
    }
}

private fun com.jetbrains.php.lang.psi.elements.Parameter.phpDocVariableName(): String {
    return "\$" + name.removePrefix("\$")
}

private val PARAM_TAG = Regex("""^@param\s+(\S+)(?:\s+(\S+))?.*""")

private val PARAM_WITH_INLINE_DESCRIPTION =
    Regex("""^(?<tag>@param\s+\S+\s+(?:&?\${'$'}|\.{3}\${'$'})\S+)\s+(?<description>.+\S)\s*${'$'}""")

private val RETURN_WITH_INLINE_DESCRIPTION =
    Regex("""^(?<tag>@return\s+\S+)\s+(?<description>.+\S)\s*${'$'}""")
