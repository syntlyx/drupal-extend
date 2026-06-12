package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.Function

internal object DrupalDocBlockFixes {
    val fixDocBlock = object : LocalQuickFix {
        override fun getFamilyName(): String = "Fix Drupal DocBlock"

        override fun getName(): String = familyName

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val docComment = descriptor.psiElement as? PhpDocComment ?: return
            DrupalDocBlockFixer.apply(project, docComment)
        }
    }
}

/**
 * Applies every docblock-scoped fix to one docblock, iterating to a fixpoint: the umbrella
 * rewrite, the summary wrap, the declaration-spacing fix, and the owner function's missing
 * `@param`/`@return`/`@throws` insertion. Edit sources targeting the same region are
 * overlap-filtered per pass — the loop converges what a single pass postpones, so one action
 * run both inserts and normalizes (no "run it twice" dance).
 */
internal object DrupalDocBlockFixer {
    fun apply(project: Project, docComment: PhpDocComment) {
        val file = docComment.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return
        val settings = project.drupalExtendSettings
        // Every edit source touches offsets at or after the docblock start, so the start offset
        // is stable across passes and re-locates the (re-parsed) docblock.
        val startOffset = docComment.textRange.startOffset

        repeat(MAX_DOC_BLOCK_FIX_PASSES) {
            val passStartText = document.text
            val currentDocComment = PsiTreeUtil
                .findElementOfClassAtOffset(file, startOffset, PhpDocComment::class.java, true)
                ?: return
            val edits = currentDocComment.docBlockFixEdits(settings, document)
            if (edits.isEmpty()) return

            edits.applyTo(document)
            documentManager.commitDocument(document)
            if (document.text == passStartText) return
        }
    }
}

private const val MAX_DOC_BLOCK_FIX_PASSES = 4

private fun PhpDocComment.docBlockFixEdits(
    settings: DrupalExtendSettings,
    document: Document,
): Collection<TextEdit> {
    val edits = mutableListOf<TextEdit>()

    fullDocBlockFixEdit(settings)?.let { edits += it }

    if (settings.isCodeStyleDocBlockDeclarationSpacingEnabled) {
        declarationSpacingEdit(document)
            ?.takeIf { spacingEdit -> edits.none { it.overlaps(spacingEdit) } }
            ?.let { edits += it }
    }

    if (settings.isCodeStyleDocBlockSummaryLengthEnabled) {
        summaryWrapEdit()
            ?.takeIf { wrapEdit -> edits.none { it.overlaps(wrapEdit) } }
            ?.let { edits += it }
    }

    if (settings.isCodeStyleFunctionDocMissingTagsEnabled) {
        owningFunction()?.missingDocTagsEdits(this)
            ?.filterNot { tagEdit -> edits.any { it.overlaps(tagEdit) } }
            ?.let { edits += it }
    }

    return edits.distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
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
        .withParamArrayShapesInDrupalForm(settings)
        .withThrowsExceptionNamespaces(settings, throwsExceptionFqns())
        .withInlineDescriptionsOnContinuationLines(settings)
        .withMissingTagDescriptions(function, settings)
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
    return mapIndexed { index, line ->
        if (line.tagName() != "param") return@mapIndexed line

        val parameterName = function.parameters.getOrNull(paramIndex)?.phpDocVariableName()
        paramIndex++
        if (hasParamNameAt(index)) return@mapIndexed line

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

/**
 * Rewrites a multiline `@param array{` … `} $name` shape into the Drupal-coder-compatible form:
 * `@param array $name` with the keys documented as a `- key: (optional) …` list under the
 * description. Coder reads the `@param` type and variable from the tag's first line only, so
 * the multiline form leaves the parameter "not described in comment"
 * (Drupal.Commenting.FunctionComment.ParamMissingDefinition). Single-line shapes are left
 * alone — coder accepts PHPStan types on one line. Anything that doesn't parse cleanly (nested
 * multiline shapes, quoted keys with spaces) is left untouched.
 */
private fun List<String>.withParamArrayShapesInDrupalForm(settings: DrupalExtendSettings): List<String> {
    if (!settings.isCodeStyleFunctionDocMissingTagsEnabled) return this

    val result = mutableListOf<String>()
    var index = 0
    while (index < size) {
        val converted = convertMultilineParamShape(index)
        if (converted == null) {
            result += get(index)
            index++
        } else {
            result += converted.lines
            index += converted.consumedLines
        }
    }

    return result
}

private data class ConvertedParamShape(val lines: List<String>, val consumedLines: Int)

private fun List<String>.convertMultilineParamShape(index: Int): ConvertedParamShape? {
    val tagLine = get(index)
    if (tagLine.tagName() != "param") return null
    val tag = tagLine.drupalDocBlockParamTag() ?: return null
    // Only the canonical generated form: the tag line carries `array{` alone, entries follow.
    if (tag.type != "array{") return null
    var balance = tag.type.docBlockTypeNestingBalance()
    if (balance <= 0) return null

    val keys = mutableListOf<ArrayShapes.ShapeKey>()
    var lineIndex = index + 1
    var nameAndRest: Pair<String, String?>? = null
    while (lineIndex < size) {
        val content = get(lineIndex)
        if (content.isTag() || content.isBlank()) return null
        val trimmed = content.trim()
        val consumed = trimmed.consumeDocBlockTypeLine(balance)
        balance = consumed.balance
        val typeEndOffset = consumed.typeEndOffset
        if (typeEndOffset != null) {
            val afterType = trimmed.substring(typeEndOffset).trimStart()
            val parts = afterType.split(Regex("""\s+"""), limit = 2)
            val name = parts.firstOrNull()?.takeIf { it.isParamName() } ?: return null
            nameAndRest = name to parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            lineIndex++
            break
        }
        keys += parseShapeEntryLine(trimmed) ?: return null
        lineIndex++
    }
    val (name, inlineDescription) = nameAndRest ?: return null
    if (keys.isEmpty()) return null

    val descriptionLines = mutableListOf<String>()
    while (lineIndex < size && !get(lineIndex).isTag() && get(lineIndex).isNotBlank()) {
        descriptionLines += get(lineIndex)
        lineIndex++
    }

    val output = mutableListOf("@param array $name")
    inlineDescription?.let { output += "$DESCRIPTION_INDENT$it" }
    output += descriptionLines
    if (inlineDescription == null && descriptionLines.isEmpty()) {
        output += "${DESCRIPTION_INDENT}The ${humanizeIdentifier(name.trimStart('&', '.', '$'))}."
    }
    keys.forEach { output += DESCRIPTION_INDENT + ArrayShapes.keyDescriptionLine(it) }

    return ConvertedParamShape(output, lineIndex - index)
}

/** Parses one `key?: type,` shape-entry line; null bails the whole conversion (fail soft). */
private fun parseShapeEntryLine(trimmed: String): ArrayShapes.ShapeKey? {
    val match = Regex("""^('?)([^'?:\s]+)\1(\??):\s*\S.*$""").find(trimmed) ?: return null

    return ArrayShapes.ShapeKey(match.groupValues[2], match.groupValues[3] == "?")
}

/**
 * Inserts a generated description line under every `@param` / `@return` / `@throws` tag that has
 * none — same wording the missing-tags generator uses, so a docblock whose tags were written by
 * hand (or by PhpStorm) normalizes to the same shape. Every tag gets its own description, not
 * just the ones the generator happens to insert.
 */
private fun List<String>.withMissingTagDescriptions(
    function: Function?,
    settings: DrupalExtendSettings,
): List<String> {
    if (!settings.isCodeStyleDocBlockTagDescriptionRequiredEnabled) return this

    val result = mutableListOf<String>()
    var index = 0
    while (index < size) {
        val line = get(index)
        result += line
        val tagName = line.tagName()
        if (tagName !in DRUPAL_DOCBLOCK_DESCRIPTION_TAGS) {
            index++
            continue
        }

        // Lines belonging to this tag: multiline-type continuation first, then description.
        val continuationLines = ((index + 1) until size)
            .asSequence()
            .takeWhile { !this[it].isTag() && this[it].isNotBlank() }
            .map { get(it) }
            .toList()
        val typeLineCount = line.continuationDescriptionStartIndex(continuationLines)
        repeat(typeLineCount) { offset -> result += get(index + 1 + offset) }

        if (!hasTagDescription(index, line, tagName!!, continuationLines, typeLineCount)) {
            missingTagDescription(index, line, tagName, function)?.let { result += "$DESCRIPTION_INDENT$it" }
        }

        index += 1 + typeLineCount
    }

    return result
}

private fun List<String>.hasTagDescription(
    tagIndex: Int,
    tagLine: String,
    tagName: String,
    continuationLines: List<String>,
    typeLineCount: Int,
): Boolean {
    if (continuationLines.size > typeLineCount) return true
    if (tagLine.hasInlineTagDescription(tagName)) return true

    // Multiline @param: the description may trail the name on the type-closing line.
    return tagName == "param" && multilineParamNameAndRest(tagIndex)?.second?.isNotBlank() == true
}

private fun List<String>.missingTagDescription(
    tagIndex: Int,
    tagLine: String,
    tagName: String,
    function: Function?,
): String? {
    return when (tagName) {
        "param" -> {
            val name = tagLine.drupalDocBlockParamTag()?.name?.takeIf { it.isParamName() }
                ?: multilineParamNameAndRest(tagIndex)?.first?.takeIf { it.isParamName() }
                ?: return null
            "The ${humanizeIdentifier(name.trimStart('&', '.', '$'))}."
        }

        "return" -> {
            val rawType = function?.typeDeclaration?.text?.trim()?.takeIf { it.isNotBlank() }
                ?: tagLine.drupalDocBlockReturnTag()?.type?.substringBefore('{')?.takeIf { it.isNotBlank() }
                ?: return null
            if (rawType.equals("void", ignoreCase = true) || rawType.equals("never", ignoreCase = true)) return null
            "The ${shortTypeNameForDescription(rawType)}."
        }

        "throws" -> {
            val type = tagLine.drupalDocBlockThrowsTag()?.type?.takeIf { it.isNotBlank() } ?: return null
            val shortName = type.substringAfterLast('\\').replaceFirstChar { it.lowercaseChar() }
            if (shortName.isBlank()) return null
            "Thrown when the ${humanizeIdentifier(shortName)} occurs."
        }

        else -> null
    }
}

private fun List<String>.withThrowsExceptionNamespaces(
    settings: DrupalExtendSettings,
    throwsFqns: Map<String, String>,
): List<String> {
    if (!settings.isCodeStyleDocBlockApiTagsEnabled) return this

    return map { line ->
        if (line.tagName() != "throws") return@map line
        val tag = line.drupalDocBlockThrowsTag() ?: return@map line
        if (tag.type.startsWith("\\")) return@map line

        val resolvedFqn = throwsFqns[tag.type]
        if (resolvedFqn != null) {
            line.substring(0, tag.typeStartOffset) + resolvedFqn + line.substring(tag.typeEndOffset)
        } else {
            line.substring(0, tag.typeStartOffset) + "\\" + line.substring(tag.typeStartOffset)
        }
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

private fun List<String>.hasParamNameAt(index: Int): Boolean {
    val line = getOrNull(index)?.takeIf { it.tagName() == "param" } ?: return false
    if (line.drupalDocBlockParamTag()?.name?.isParamName() == true) return true

    return multilineParamNameAndRest(index)?.first?.isParamName() == true
}

/**
 * For a multiline-typed `@param` at [index], the first token after the type-closing bracket
 * (the variable name) and whatever trails it on that line (an inline description), or null
 * when the type is single-line or never closes.
 */
private fun List<String>.multilineParamNameAndRest(index: Int): Pair<String, String?>? {
    var typeBalance = getOrNull(index)
        ?.drupalDocBlockParamTag()
        ?.type
        ?.docBlockTypeNestingBalance()
        ?: return null
    if (typeBalance <= 0) return null

    ((index + 1) until size).forEach { lineIndex ->
        val line = get(lineIndex)
        if (line.isTag() || line.isBlank()) return null

        val result = line.trim().consumeDocBlockTypeLine(typeBalance)
        typeBalance = result.balance
        val typeEndOffset = result.typeEndOffset ?: return@forEach
        val parts = line.trim()
            .substring(typeEndOffset)
            .trimStart()
            .split(Regex("""\s+"""), limit = 2)
        val name = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null

        return name to parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    return null
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
        // Mirror DrupalDocBlockLine.tagGroup(): all custom/unknown tags share one "other" group so
        // the umbrella fix does not insert blank lines between e.g. Drush @command/@option/@usage.
        else -> "other"
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

    if (trimmed.startsWith("- ") && previousDescriptionLine?.trimStart()?.startsWith("- ") == true) {
        return " ".repeat(previousIndent)
    }

    if (trimmed.startsWith("- ") && previousDescriptionLine?.trimEnd()?.endsWith(":") == true) {
        if (currentIndent == previousIndent || currentIndent == previousIndent + DESCRIPTION_INDENT.length) {
            return " ".repeat(currentIndent)
        }

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
