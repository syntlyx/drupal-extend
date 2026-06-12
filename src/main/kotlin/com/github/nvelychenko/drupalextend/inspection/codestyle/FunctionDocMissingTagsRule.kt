package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Parameter

internal object FunctionDocMissingTagsRule : DrupalCodeStyleRule {
    override val id: String = "function_doc_missing_tags"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleFunctionDocMissingTagsEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val docComment = function.docComment ?: return
        val lines = docComment.drupalDocBlockLines()

        // Skip if {@inheritdoc} is present (case-insensitive).
        if (lines.any { it.normalizedTrimmedContent().equals("{@inheritdoc}", ignoreCase = true) }) return

        val existingParamNames = collectExistingParamNames(lines)
        val hasReturnTag = lines.any { it.tagName() == "return" }

        // Report a single problem per missing @param. The tag is absent, so there is no line to
        // anchor — highlight the whole docblock instead of an unrelated existing line.
        for (parameter in function.parameters) {
            val docVarName = parameter.phpDocVariableName()
            if (existingParamNames.contains(docVarName)) continue

            context.registerDocBlockProblem(
                docComment,
                "Missing @param for $docVarName",
                addMissingTagsFix()
            )
        }

        // Report missing @return when a non-void/never return type is declared.
        if (!hasReturnTag) {
            val rawReturnType = function.typeDeclaration?.text?.trim().orEmpty()
            if (rawReturnType.isNotBlank()
                && !rawReturnType.equals("void", ignoreCase = true)
                && !rawReturnType.equals("never", ignoreCase = true)
            ) {
                context.registerDocBlockProblem(
                    docComment,
                    "Missing @return tag",
                    addMissingTagsFix()
                )
            }
        }
    }

    private fun addMissingTagsFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Add missing doc tags") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val function = PsiTreeUtil.findChildrenOfType(docComment.containingFile, Function::class.java)
                .firstOrNull { it.docComment?.textRange == docComment.textRange }
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            function.missingDocTagsEdits(docComment)
        }
    }
}

/**
 * Returns the index of the last line belonging to the @param block that starts at [tagLineIndex].
 *
 * A @param block consists of the tag line plus any following non-tag, non-blank lines (its
 * description continuation). This is the line after which a new @param (or @return) tag should
 * be appended.
 */
private fun List<DrupalDocBlockLine>.lastParamBlockEndIndex(tagLineIndex: Int): Int {
    var lastIdx = tagLineIndex
    for (i in (tagLineIndex + 1) until size) {
        val line = get(i)
        // Continuation lines are non-tag, non-blank content lines.
        if (line.isTag || line.isBlankContent || line.isClosingLine()) break
        lastIdx = i
    }
    return lastIdx
}

/**
 * Collects the normalized variable names (e.g. `$foo`) from existing `@param` tags in the
 * docblock lines, stripping variadic (`...$`) and pass-by-ref (`&`) prefixes so they can be
 * compared against [Parameter.phpDocVariableName].
 */
private fun collectExistingParamNames(lines: List<DrupalDocBlockLine>): Set<String> {
    return lines
        .filter { it.tagName() == "param" }
        .mapNotNull { it.paramTag()?.name }
        .map { normalizeParamName(it) }
        .toSet()
}

/**
 * Strips `...`, `&`, and ensures a leading `$` so the name can be compared against
 * [Parameter.phpDocVariableName].
 */
private fun normalizeParamName(rawName: String): String {
    val stripped = rawName.removePrefix("&").removePrefix("...").removePrefix("&")
    return if (stripped.startsWith("\$")) stripped else "\$$stripped"
}

private fun Parameter.phpDocVariableName(): String = "\$" + name.removePrefix("\$")

/**
 * Computes the [TextEdit]s needed to insert missing `@param` and `@return` tags into the
 * docblock of this function. Returns an empty list when no tags are missing.
 *
 * Exposed as an extension so the file-wide fixer can reuse this computation.
 */
internal fun Function.missingDocTagsEdits(
    docComment: PhpDocComment? = null,
): List<TextEdit> {
    if (isClosure) return emptyList()
    val effectiveDocComment = docComment ?: this.docComment ?: return emptyList()

    val lines = effectiveDocComment.drupalDocBlockLines()

    // Skip {@inheritdoc}.
    if (lines.any { it.normalizedTrimmedContent().equals("{@inheritdoc}", ignoreCase = true) }) {
        return emptyList()
    }

    val existingParamNames = collectExistingParamNames(lines)
    val hasReturnTag = lines.any { it.tagName() == "return" }

    val missingParams = parameters.filter { !existingParamNames.contains(it.phpDocVariableName()) }

    val rawReturnType = typeDeclaration?.text?.trim().orEmpty()
    val needsReturn = !hasReturnTag
        && rawReturnType.isNotBlank()
        && !rawReturnType.equals("void", ignoreCase = true)
        && !rawReturnType.equals("never", ignoreCase = true)

    if (missingParams.isEmpty() && !needsReturn) return emptyList()

    val docStartOffset = effectiveDocComment.textRange.startOffset
    val indentation = effectiveDocComment.docBlockFileIndentation()

    // Compute the line index after which @param tags will be inserted.
    val lastParamLineIndex = lines.indexOfLast { it.tagName() == "param" }
    val paramInsertAfterIndex = when {
        lastParamLineIndex >= 0 -> {
            // Skip continuation lines (description) of the last @param.
            lines.lastParamBlockEndIndex(lastParamLineIndex)
        }
        else -> {
            // No @param tags yet: insert after the summary block, before the first tag or close.
            val firstTagIdx = lines.indexOfFirst { it.isTag && it.tagName() != null }
            if (firstTagIdx > 0) firstTagIdx - 1
            else (lines.size - 2).coerceAtLeast(0)
        }
    }

    // Collect edits: @param lines, then @return, anchored at each line's lineEndOffset.
    // We group by insertion point so a single TextEdit carries all new text at that offset.
    val insertionMap = mutableMapOf<Int, StringBuilder>()

    fun sbAt(lineIndex: Int): StringBuilder {
        val lineEndOffset = lines.getOrNull(lineIndex)?.lineEndOffset ?: return StringBuilder()
        return insertionMap.getOrPut(docStartOffset + lineEndOffset) { StringBuilder() }
    }

    if (missingParams.isNotEmpty()) {
        val sb = sbAt(paramInsertAfterIndex)
        val needsBlankLineBefore = lastParamLineIndex < 0
            && lines.getOrNull(paramInsertAfterIndex)?.let { !it.isTag && !it.isBlankContent } == true

        if (needsBlankLineBefore) {
            sb.append("\n").append(indentation).append(" *")
        }

        for (param in missingParams) {
            val typeStr = param.docBlockTypeString()
            val varName = param.phpDocVariableName()
            val docVar = if (param.isVariadic) "...$varName" else varName
            val description = humanizeParamName(varName.removePrefix("\$"))
            sb.append("\n").append(indentation).append(" * @param ").append(typeStr).append(" ").append(docVar)
            sb.append("\n").append(indentation).append(" *   ").append(description)
        }
    }

    if (needsReturn) {
        // @return goes after the last @param (or after newly added ones, i.e. same anchor),
        // with a blank line separating it from the @param group.
        val returnInsertAfterIndex = if (missingParams.isNotEmpty()) {
            paramInsertAfterIndex
        } else {
            // Only @return is missing; insert after existing @param group if any.
            if (lastParamLineIndex >= 0) lines.lastParamBlockEndIndex(lastParamLineIndex)
            else {
                val firstTagIdx = lines.indexOfFirst { it.isTag && it.tagName() != null }
                if (firstTagIdx > 0) firstTagIdx - 1
                else (lines.size - 2).coerceAtLeast(0)
            }
        }

        val sb = sbAt(returnInsertAfterIndex)
        val prevHasParams = missingParams.isNotEmpty() || lastParamLineIndex >= 0
        if (prevHasParams) {
            sb.append("\n").append(indentation).append(" *")
        } else if (lines.getOrNull(returnInsertAfterIndex)?.let { !it.isTag && !it.isBlankContent } == true) {
            sb.append("\n").append(indentation).append(" *")
        }

        val typeStr = functionReturnDocBlockTypeString(rawReturnType)
        val shortName = shortTypeNameForDescription(rawReturnType)
        sb.append("\n").append(indentation).append(" * @return ").append(typeStr)
        sb.append("\n").append(indentation).append(" *   The ").append(shortName).append(".")
    }

    return insertionMap.map { (offset, sb) -> TextEdit(offset, sb.toString()) }
}

/**
 * Renders the declared type of a parameter into docblock format.
 *
 * Uses the raw PHP source text from [Parameter.getTypeDeclaration] when available, falling back
 * to `mixed` when the parameter has no declared type.
 *
 * Rendering rules:
 * - Nullable `?Foo` → `\Foo|null`
 * - Primitive/scalar types (string, int, bool, float, array, mixed, …) → without leading `\`
 * - Class types → with leading `\`
 * - Union types (PHP 8+) in source → passed through with per-segment rendering
 * - When no type declaration exists → `mixed`
 */
private fun Parameter.docBlockTypeString(): String {
    val rawText = typeDeclaration?.text?.trim()
    if (rawText.isNullOrBlank()) return "mixed"
    return renderPhpTypeDeclarationText(rawText)
}

/**
 * Converts the raw PHP return-type declaration text into docblock format.
 */
private fun functionReturnDocBlockTypeString(rawText: String): String {
    if (rawText.isBlank()) return "mixed"
    return renderPhpTypeDeclarationText(rawText)
}

/**
 * Renders a raw PHP type hint text (as it appears in source) into docblock notation.
 *
 * Assumptions about `rawText`:
 * - May start with `?` for nullable (PHP 7+ syntax)
 * - May be a union type `Foo|Bar` (PHP 8+)
 * - May be an intersection type `Foo&Bar` (PHP 8.1+)
 * - Individual segments may be unqualified short names or FQNs starting with `\`
 */
private fun renderPhpTypeDeclarationText(rawText: String): String {
    val nullable = rawText.startsWith("?")
    val base = rawText.removePrefix("?").trim()

    // Split union types.
    val segments = base.split("|").map { it.trim() }
    val hasExplicitNull = segments.any { it.equals("null", ignoreCase = true) }
    val nonNullSegments = if (hasExplicitNull) segments.filter { !it.equals("null", ignoreCase = true) } else segments

    val rendered = nonNullSegments.joinToString("|") { renderPhpTypeSegment(it) }
    return when {
        nullable || hasExplicitNull -> "$rendered|null"
        else -> rendered
    }
}

/**
 * Renders a single PHP type segment (not a union/nullable wrapper) to docblock notation.
 */
private fun renderPhpTypeSegment(segment: String): String {
    if (segment.isBlank()) return "mixed"
    // Already fully qualified.
    if (segment.startsWith("\\")) return segment
    // Scalars and PHP built-in pseudo-types: keep without leading `\`.
    val lower = segment.lowercase()
    if (lower in SCALAR_TYPES) return lower
    // static, self, parent, never, void, etc.
    if (lower in KEYWORD_TYPES) return lower
    // Array type hints like `string[]`, `int[]`.
    if (lower.endsWith("[]")) return segment
    // Intersection type — pass through preserving `&`.
    if (segment.contains("&")) return segment
    // Class reference without leading `\` → add it.
    return "\\$segment"
}

private val SCALAR_TYPES = setOf(
    "string", "int", "integer", "float", "double", "bool", "boolean",
    "array", "callable", "iterable", "object", "mixed", "null",
    "resource", "never", "void",
)

private val KEYWORD_TYPES = setOf("static", "self", "parent", "never", "void", "null", "false", "true")

/**
 * Produces a humanized short description from the short class/type name.
 *
 * For example: `contextFactory` → `context factory`, `Foo` → `foo`.
 * The caller is responsible for adding "The " prefix and a trailing ".".
 */
private fun shortTypeNameForDescription(rawText: String): String {
    val base = rawText.removePrefix("?").trimStart('\\')
    val shortName = base.substringAfterLast('\\').substringAfterLast('/')
        .substringBefore('|')   // For unions, describe just the primary type.
    return humanizeIdentifier(shortName.replaceFirstChar { it.lowercaseChar() })
}

/**
 * Humanizes a camelCase or snake_case identifier into lowercase words joined by spaces.
 *
 * Examples:
 * - `contextFactory` → `context factory`
 * - `context_factory` → `context factory`
 * - `applicationRequirementContextFactory` → `application requirement context factory`
 */
private fun humanizeIdentifier(name: String): String {
    val withSpaces = name.replace('_', ' ')
    val words = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
        .split(withSpaces)
        .flatMap { it.split(' ') }
        .filter { it.isNotBlank() }
        .map { it.lowercase() }

    return if (words.isEmpty()) name else words.joinToString(" ")
}

/**
 * Produces the auto-humanized Drupal-format description for a `@param` tag.
 *
 * Examples:
 * - `contextFactory` → `The context factory.`
 * - `context_factory` → `The context factory.`
 * - `applicationRequirementContextFactory` → `The application requirement context factory.`
 */
private fun humanizeParamName(rawName: String): String {
    return "The ${humanizeIdentifier(rawName)}."
}

/**
 * Returns the file-level indentation (whitespace before the docComment token) as a string.
 *
 * This is the leading whitespace on the line where the doc-comment open token appears, i.e.
 * the amount of indentation the docblock has in the file. Used to produce correctly indented
 * inserted lines that match the docblock's own indentation.
 */
private fun PhpDocComment.docBlockFileIndentation(): String {
    val fileText = containingFile?.text ?: return ""
    val docStart = textRange.startOffset
    val lineStart = fileText.lastIndexOf('\n', (docStart - 1).coerceAtLeast(0))
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: 0
    return fileText.substring(lineStart, docStart).takeWhile { it == ' ' || it == '\t' }
}
