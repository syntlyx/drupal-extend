package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocType
import com.jetbrains.php.lang.psi.elements.Catch
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpThrowExpression
import com.jetbrains.php.lang.psi.elements.Try
import com.jetbrains.php.lang.psi.elements.Variable

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

        // Report exceptions the function demonstrably throws but the docblock doesn't document.
        for (exceptionFqn in function.undocumentedThrownExceptionFqns(docComment, lines)) {
            context.registerDocBlockProblem(
                docComment,
                "Missing @throws for $exceptionFqn",
                addMissingTagsFix()
            )
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

    val missingThrows = undocumentedThrownExceptionFqns(effectiveDocComment, lines)

    if (missingParams.isEmpty() && !needsReturn && missingThrows.isEmpty()) return emptyList()

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
            var typeStr = param.docBlockTypeString()
            if (typeStr == "array") {
                ArrayShapes.parameterArrayShape(this, param)?.let { typeStr = it }
            }
            val varName = param.phpDocVariableName()
            val docVar = if (param.isVariadic) "...$varName" else varName
            val description = humanizeParamName(varName.removePrefix("\$"))
            sb.append("\n").append(indentation).append(" * @param ").append(typeStr).append(" ").append(docVar)
            sb.append("\n").append(indentation).append(" *   ").append(description)
        }
    }

    // Shared anchor for tags appended after the @param group: right after the params being
    // inserted, after the existing @param group, or after the summary when there are no params.
    val tagAppendAnchorIndex = when {
        missingParams.isNotEmpty() -> paramInsertAfterIndex
        lastParamLineIndex >= 0 -> lines.lastParamBlockEndIndex(lastParamLineIndex)
        else -> {
            val firstTagIdx = lines.indexOfFirst { it.isTag && it.tagName() != null }
            if (firstTagIdx > 0) firstTagIdx - 1
            else (lines.size - 2).coerceAtLeast(0)
        }
    }

    if (needsReturn) {
        // @return goes after the @param group, with a blank line separating the groups.
        val sb = sbAt(tagAppendAnchorIndex)
        val prevHasParams = missingParams.isNotEmpty() || lastParamLineIndex >= 0
        if (prevHasParams) {
            sb.append("\n").append(indentation).append(" *")
        } else if (lines.getOrNull(tagAppendAnchorIndex)?.let { !it.isTag && !it.isBlankContent } == true) {
            sb.append("\n").append(indentation).append(" *")
        }

        var typeStr = typeDeclaration?.let { renderPhpTypeDeclaration(it) } ?: "mixed"
        if (typeStr == "array") {
            ArrayShapes.returnArrayShape(this)?.let { typeStr = it }
        }
        val shortName = shortTypeNameForDescription(rawReturnType)
        sb.append("\n").append(indentation).append(" * @return ").append(typeStr)
        sb.append("\n").append(indentation).append(" *   The ").append(shortName).append(".")
    }

    if (missingThrows.isNotEmpty()) {
        val lastThrowsLineIndex = lines.indexOfLast { it.tagName() == "throws" }
        val returnLineIndex = lines.indexOfLast { it.tagName() == "return" }
        // New @throws join the existing @throws group directly; otherwise they form a new group
        // after @return (existing or just appended above — same anchor keeps the order), else
        // after the @param group / summary.
        val joinsExistingThrowsGroup = lastThrowsLineIndex >= 0
        val throwsInsertAfterIndex = when {
            joinsExistingThrowsGroup -> lines.lastParamBlockEndIndex(lastThrowsLineIndex)
            returnLineIndex >= 0 -> lines.lastParamBlockEndIndex(returnLineIndex)
            else -> tagAppendAnchorIndex
        }

        val sb = sbAt(throwsInsertAfterIndex)
        if (!joinsExistingThrowsGroup) {
            val anchorIsBlank = sb.isEmpty()
                && lines.getOrNull(throwsInsertAfterIndex)?.isBlankContent == true
            if (!anchorIsBlank) {
                sb.append("\n").append(indentation).append(" *")
            }
        }

        for (exceptionFqn in missingThrows) {
            val shortName = exceptionFqn.substringAfterLast('\\').replaceFirstChar { it.lowercaseChar() }
            sb.append("\n").append(indentation).append(" * @throws ").append(exceptionFqn)
            sb.append("\n").append(indentation).append(" *   Thrown when the ")
                .append(humanizeIdentifier(shortName)).append(" occurs.")
        }
    }

    return insertionMap.map { (offset, sb) -> TextEdit(offset, sb.toString()) }
}

/**
 * Collects FQNs of exceptions this function demonstrably throws that its docblock does not
 * document. Conservative by design — only syntactically certain throws count:
 * - `throw new FooException(...)` whose class resolves through the use table / namespace;
 * - `throw $e` rethrows of a caught exception (the catch clause names the classes).
 *
 * Throws inside nested closures/arrow functions/anonymous classes belong to that scope, not
 * this one. Throws lexically inside a `try` block are assumed handled by its catches —
 * hierarchy-aware flow analysis is out of scope for a docblock generator, and a false `@throws`
 * is worse than a missing one. A rethrow in a `catch` block still counts.
 */
private fun Function.undocumentedThrownExceptionFqns(
    docComment: PhpDocComment,
    lines: List<DrupalDocBlockLine>,
): List<String> {
    val thrown = LinkedHashSet<String>()
    for (throwExpression in PsiTreeUtil.findChildrenOfType(this, PhpThrowExpression::class.java)) {
        if (PsiTreeUtil.getParentOfType(throwExpression, Function::class.java) != this) continue
        if (throwExpression.isInsideTryBlock(this)) continue
        thrown += throwExpression.thrownExceptionFqns(this)
    }
    collectPropagatedThrowsFqns(thrown)
    if (thrown.isEmpty()) return emptyList()

    val documented = documentedThrowsKeys(docComment, lines)
    return thrown.filter { fqn ->
        fqn !in documented && fqn.substringAfterLast('\\') !in documented
    }
}

/**
 * Exceptions documented as thrown by the callables this function invokes directly — the cases
 * PhpStorm's own "Unhandled exception" inspection flags on the call site (`$storage->save()`
 * propagating `\Drupal\Core\Entity\EntityStorageException`).
 *
 * Same scoping as literal throws: calls inside nested closures belong to that scope, calls
 * lexically inside a `try` block are assumed handled. Unchecked exception families
 * (`\RuntimeException`, `\LogicException`, `\Error`) are not propagated — PhpStorm's inspection
 * ignores them by default and documenting them on every caller is noise; a literal `throw`
 * in the body still counts regardless of family.
 */
private fun Function.collectPropagatedThrowsFqns(into: MutableSet<String>) {
    val callees = LinkedHashSet<Function>()

    for (call in PsiTreeUtil.findChildrenOfType(this, FunctionReference::class.java)) {
        ProgressManager.checkCanceled()
        if (PsiTreeUtil.getParentOfType(call, Function::class.java) != this) continue
        if (call.isInsideTryBlock(this)) continue
        (call.resolve() as? Function)?.takeIf { it != this }?.let { callees += it }
    }

    for (newExpression in PsiTreeUtil.findChildrenOfType(this, NewExpression::class.java)) {
        ProgressManager.checkCanceled()
        if (PsiTreeUtil.getParentOfType(newExpression, Function::class.java) != this) continue
        if (newExpression.isInsideTryBlock(this)) continue
        val instantiatedClass = newExpression.classReference?.resolve() as? PhpClass ?: continue
        instantiatedClass.constructor?.let { callees += it }
    }

    for (callee in callees) {
        for (fqn in callee.declaredThrowsFqns()) {
            if (!fqn.isUncheckedExceptionFqn(project)) into += fqn
        }
    }
}

/**
 * The `@throws` FQNs a callable declares, chasing `{@inheritdoc}` overrides up the class
 * hierarchy to the documented declaration — in Drupal core the interface method usually
 * carries the `@throws` while implementations inherit the docs.
 */
private fun Function.declaredThrowsFqns(): List<String> {
    ownDocThrowsFqns().takeIf { it.isNotEmpty() }?.let { return it }
    if (this !is Method) return emptyList()

    val methodName = name
    val visited = HashSet<String>()
    val queue = ArrayDeque(containingClass?.supers?.toList().orEmpty())
    while (queue.isNotEmpty() && visited.size < MAX_SUPER_CLASSES) {
        ProgressManager.checkCanceled()
        val clazz = queue.removeFirst()
        if (!visited.add(clazz.fqn)) continue

        val superMethod = clazz.findMethodByName(methodName)
        if (superMethod != null && superMethod.containingClass?.fqn == clazz.fqn) {
            superMethod.ownDocThrowsFqns().takeIf { it.isNotEmpty() }?.let { return it }
        }

        queue.addAll(clazz.supers)
    }

    return emptyList()
}

private fun Function.ownDocThrowsFqns(): List<String> {
    val doc = docComment ?: return emptyList()
    val resolvedFqns = doc.throwsExceptionFqns()
    return doc.getTagElementsByName("@throws").mapNotNull { tag ->
        val typeText = PsiTreeUtil.findChildOfType(tag, PhpDocType::class.java)
            ?.text?.trim()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        if (typeText.startsWith("\\")) typeText else resolvedFqns[typeText]
    }
}

private val UNCHECKED_EXCEPTION_FQNS = setOf("\\RuntimeException", "\\LogicException", "\\Error")
private const val MAX_SUPER_CLASSES = 16
private const val MAX_HIERARCHY_HOPS = 12

private fun String.isUncheckedExceptionFqn(project: Project): Boolean {
    if (this in UNCHECKED_EXCEPTION_FQNS) return true

    var current = PhpIndex.getInstance(project).getAnyByFQN(this).firstOrNull()
    var hops = 0
    while (current != null && hops < MAX_HIERARCHY_HOPS) {
        if (current.fqn in UNCHECKED_EXCEPTION_FQNS) return true
        current = current.superClass
        hops++
    }

    return false
}

/**
 * Every key an existing `@throws` tag is known by: the type as written, its short name, and —
 * for unqualified types that resolve — the resolved FQN. Detected exceptions are matched against
 * all of them so a short-name `@throws FooException` still counts as documenting
 * `\Drupal\foo\FooException`.
 */
private fun documentedThrowsKeys(docComment: PhpDocComment, lines: List<DrupalDocBlockLine>): Set<String> {
    val resolvedFqns = docComment.throwsExceptionFqns()
    val keys = mutableSetOf<String>()
    lines.filter { it.tagName() == "throws" }
        .mapNotNull { it.throwsTag()?.type }
        .forEach { type ->
            keys += type
            keys += type.substringAfterLast('\\')
            resolvedFqns[type]?.let { keys += it }
        }
    return keys
}

private fun PhpThrowExpression.thrownExceptionFqns(scope: Function): List<String> {
    return when (val thrownValue = argument) {
        is NewExpression -> listOfNotNull(thrownValue.classReference?.exceptionFqn())
        is Variable -> rethrownCatchExceptionFqns(thrownValue, scope)
        else -> emptyList()
    }
}

private fun ClassReference.exceptionFqn(): String? {
    val lower = name?.lowercase() ?: return null
    if (lower in KEYWORD_TYPES) return null
    return fqn?.takeIf { it.startsWith("\\") }
}

/**
 * For `throw $e`, finds the enclosing `catch` clause (within [scope]) that binds `$e` and
 * returns the exception classes it catches — the rethrown types.
 */
private fun PhpThrowExpression.rethrownCatchExceptionFqns(variable: Variable, scope: Function): List<String> {
    var node: PsiElement? = parent
    while (node != null && node != scope) {
        if (node is Catch && node.exception?.name == variable.name) {
            return node.exceptionTypes.mapNotNull { it.exceptionFqn() }
        }
        node = node.parent
    }
    return emptyList()
}

private fun PsiElement.isInsideTryBlock(scope: Function): Boolean {
    var node: PsiElement? = this
    while (node != null && node != scope) {
        val parent = node.parent
        if (parent is Try && parent.statement == node) return true
        node = parent
    }
    return false
}

/**
 * Renders the declared type of a parameter into docblock format.
 *
 * Uses the type declaration PSI from [Parameter.getTypeDeclaration] when available, falling
 * back to `mixed` when the parameter has no declared type.
 *
 * Rendering rules:
 * - Nullable `?Foo` → `\Foo|null`
 * - Primitive/scalar types (string, int, bool, float, array, mixed, …) → without leading `\`
 * - Class types → fully qualified through the file's `use` table / current namespace
 * - Union types (PHP 8+) in source → passed through with per-segment rendering
 * - When no type declaration exists → `mixed`
 */
private fun Parameter.docBlockTypeString(): String {
    return typeDeclaration?.let { renderPhpTypeDeclaration(it) } ?: "mixed"
}

/**
 * Renders a PHP type declaration element into docblock notation.
 *
 * A short class name in source (`ContextFactory`) is not the docblock FQN — it resolves through
 * the file's `use` statements (aliases included) or the current namespace. The declaration's
 * [ClassReference]s carry exactly that resolution, so each written name is mapped to its FQN
 * before the textual rendering.
 */
internal fun renderPhpTypeDeclaration(declaration: PsiElement): String {
    val rawText = declaration.text?.trim()
    if (rawText.isNullOrBlank()) return "mixed"

    val fqnByWrittenName = PsiTreeUtil.collectElementsOfType(declaration, ClassReference::class.java)
        .mapNotNull { reference ->
            val fqn = reference.fqn?.takeIf { it.startsWith("\\") } ?: return@mapNotNull null
            reference.text.trim() to fqn
        }
        .toMap()

    return renderPhpTypeDeclarationText(rawText, fqnByWrittenName)
}

/**
 * Renders a raw PHP type hint text (as it appears in source) into docblock notation.
 *
 * Assumptions about `rawText`:
 * - May start with `?` for nullable (PHP 7+ syntax)
 * - May be a union type `Foo|Bar` (PHP 8+)
 * - May be an intersection type `Foo&Bar` (PHP 8.1+)
 * - Individual segments may be unqualified short names or FQNs starting with `\`
 *
 * [fqnByWrittenName] maps class names as written in source to their resolved FQNs.
 */
private fun renderPhpTypeDeclarationText(rawText: String, fqnByWrittenName: Map<String, String>): String {
    val nullable = rawText.startsWith("?")
    val base = rawText.removePrefix("?").trim()

    // Split union types.
    val segments = base.split("|").map { it.trim() }
    val hasExplicitNull = segments.any { it.equals("null", ignoreCase = true) }
    val nonNullSegments = if (hasExplicitNull) segments.filter { !it.equals("null", ignoreCase = true) } else segments

    val rendered = nonNullSegments.joinToString("|") { renderPhpTypeSegment(it, fqnByWrittenName) }
    return when {
        nullable || hasExplicitNull -> "$rendered|null"
        else -> rendered
    }
}

/**
 * Renders a single PHP type segment (not a union/nullable wrapper) to docblock notation.
 */
private fun renderPhpTypeSegment(segment: String, fqnByWrittenName: Map<String, String>): String {
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
    // Intersection type — render each part.
    if (segment.contains("&")) {
        return segment.split("&").joinToString("&") { renderPhpTypeSegment(it.trim(), fqnByWrittenName) }
    }
    // Class reference without leading `\` → qualify via the use table, fall back to as-written.
    return fqnByWrittenName[segment] ?: "\\$segment"
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
internal fun humanizeIdentifier(name: String): String {
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
