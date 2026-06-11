package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

/**
 * Flags long-form scalar type aliases in @param, @return, and @var tags.
 *
 * Drupal coding standard requires short scalar type names:
 *   integer -> int, boolean -> bool, double -> float, real -> float
 *
 * Matching is whole-word and case-insensitive so that `Integration` is not
 * flagged (the word boundary ensures a partial-prefix match is rejected).
 * The check covers all positions within a compound type expression such as
 * `integer[]` or `integer|null`.
 *
 * Note: `@throws` uses fully-qualified class names; its type portion is never
 * a scalar, so it is intentionally excluded.
 */
internal object DocBlockShortTypesRule : DrupalCodeStyleRule {
    override val id: String = "doc_block_short_types"

    // Maps long form (lowercase) to canonical short form.
    private val LONG_TO_SHORT = mapOf(
        "integer" to "int",
        "boolean" to "bool",
        "double" to "float",
        "real" to "float",
    )

    // Matches any long-form type keyword at a word boundary (case-insensitive).
    private val LONG_TYPE_PATTERN = Regex("""(?i)\b(integer|boolean|double|real)\b""")

    override fun isEnabled(settings: DrupalExtendSettings): Boolean =
        settings.isCodeStyleDocBlockShortTypesEnabled

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        if (docComment.isInlineVariableDocBlock()) return

        docComment.drupalDocBlockLines().forEach { line ->
            when (line.tagName()) {
                "param" -> checkLine(docComment, line, line.paramTag()?.type, context)
                "return" -> checkLine(docComment, line, line.returnTag()?.type, context)
                "var" -> checkLine(docComment, line, extractVarType(line.trimmedContent), context)
            }
        }
    }

    private fun checkLine(
        docComment: PhpDocComment,
        line: DrupalDocBlockLine,
        type: String?,
        context: DrupalCodeStyleContext,
    ) {
        if (type.isNullOrBlank()) return
        if (!LONG_TYPE_PATTERN.containsMatchIn(type)) return

        context.registerDocBlockProblem(
            docComment,
            line,
            buildMessage(type),
            buildFix()
        )
    }

    // -------------------------------------------------------------------------
    // Quick fix
    // -------------------------------------------------------------------------

    private fun buildFix(): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Use short scalar type name") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            collectEdits(docComment)
        }

    /**
     * Collects all TextEdit replacements for every long-form type token in the
     * docblock. Positions are computed by finding matches in the type portion of
     * each relevant tag line within `trimmedContent`, then mapping to absolute
     * document offsets.
     *
     * The fix is idempotent: once all long-form tokens are replaced the pattern
     * finds no matches and returns an empty list.
     */
    private fun collectEdits(docComment: PhpDocComment): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val docStart = docComment.textRange.startOffset

        docComment.drupalDocBlockLines().forEach { line ->
            val trimmedContent = line.trimmedContent
            val trimmedStart = docStart + line.trimmedContentStartOffset()

            when (line.tagName()) {
                "param" -> {
                    val tag = line.paramTag() ?: return@forEach
                    // The type within trimmedContent occupies [typeRegionStart, typeEndOffset).
                    // We find the region start as the offset right after "@param" + whitespace.
                    val regionStart = typeRegionStartInTrimmed(trimmedContent, "@param")
                    collectTypeEdits(edits, tag.type, trimmedContent, regionStart, trimmedStart)
                }
                "return" -> {
                    val tag = line.returnTag() ?: return@forEach
                    collectTypeEdits(edits, tag.type, trimmedContent, tag.typeStartOffset, trimmedStart)
                }
                "var" -> {
                    val varTypeStart = varTypeStartInTrimmed(trimmedContent) ?: return@forEach
                    val type = extractVarType(trimmedContent) ?: return@forEach
                    collectTypeEdits(edits, type, trimmedContent, varTypeStart, trimmedStart)
                }
            }
        }

        return edits
    }

    /**
     * For each long-form match within [type], compute its absolute document
     * offset and add a replacement edit.
     *
     * [type] is the extracted type string (trimmed).
     * [trimmedContent] is the full trimmed content of the docblock line.
     * [typeRegionStart] is the offset within [trimmedContent] where the
     *   *untrimmed* type region begins (before the leading whitespace of [type]).
     * [trimmedStart] is the absolute document offset of the first character
     *   of [trimmedContent].
     */
    private fun collectTypeEdits(
        edits: MutableList<TextEdit>,
        type: String,
        trimmedContent: String,
        typeRegionStart: Int,
        trimmedStart: Int,
    ) {
        if (!LONG_TYPE_PATTERN.containsMatchIn(type)) return

        // Locate where `type` actually starts inside trimmedContent (skip leading
        // whitespace that was stripped by trim()).
        val typeInRegion = trimmedContent.substring(typeRegionStart)
        val leadingWs = typeInRegion.length - typeInRegion.trimStart().length
        val typeAbsStart = trimmedStart + typeRegionStart + leadingWs

        LONG_TYPE_PATTERN.findAll(type).forEach { match ->
            val longForm = match.value.lowercase()
            val shortForm = LONG_TO_SHORT[longForm] ?: return@forEach
            val editStart = typeAbsStart + match.range.first
            val editEnd = typeAbsStart + match.range.last + 1
            edits += TextEdit(editStart, editEnd, shortForm)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers for locating type regions within trimmedContent
    // -------------------------------------------------------------------------

    /**
     * Returns the offset within [trimmedContent] immediately after the tag
     * keyword and its following whitespace (i.e., where the type region starts).
     */
    private fun typeRegionStartInTrimmed(trimmedContent: String, tagKeyword: String): Int {
        val tagMatch = Regex("""^${Regex.escape(tagKeyword)}(?:\s+|$)""")
            .find(trimmedContent)
            ?: return tagKeyword.length
        return tagMatch.range.last + 1
    }

    /**
     * Returns the start offset of the type token within `trimmedContent` for a
     * `@var` tag, or null if the line is not a valid `@var` tag.
     *
     * Drupal `@var` format: `@var type` (variable name is optional and less common).
     */
    private fun varTypeStartInTrimmed(trimmedContent: String): Int? {
        val tagMatch = Regex("""^@var(?:\s+|$)""").find(trimmedContent) ?: return null
        val afterTag = tagMatch.range.last + 1
        if (afterTag >= trimmedContent.length) return null
        return afterTag
    }

    // -------------------------------------------------------------------------
    // @var type extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts the type portion from a `@var type [description]` line's
     * trimmed content.
     */
    private fun extractVarType(trimmedContent: String): String? {
        val afterTagOffset = varTypeStartInTrimmed(trimmedContent) ?: return null
        val rest = trimmedContent.substring(afterTagOffset)
        val tokens = topLevelDocBlockTokensLocal(0, rest)
        val typeRange = tokens.firstOrNull() ?: return null
        return rest.substring(typeRange).trim()
    }

    // -------------------------------------------------------------------------
    // Minimal top-level tokeniser (mirrors DrupalDocBlock.kt's private one)
    // -------------------------------------------------------------------------

    private fun topLevelDocBlockTokensLocal(startOffset: Int, text: String): List<IntRange> {
        val tokens = mutableListOf<IntRange>()
        var tokenStart: Int? = null
        var nestingDepth = 0
        var offset = startOffset

        while (offset < text.length) {
            val char = text[offset]
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

        tokenStart?.let { tokens += it until text.length }
        return tokens
    }

    // -------------------------------------------------------------------------
    // Message
    // -------------------------------------------------------------------------

    private fun buildMessage(type: String): String {
        val found = LONG_TYPE_PATTERN.findAll(type)
            .map { it.value.lowercase() }
            .distinct()
            .toList()
        return "Use short scalar type: ${found.joinToString(", ") { "$it → ${LONG_TO_SHORT[it]}" }}"
    }
}
