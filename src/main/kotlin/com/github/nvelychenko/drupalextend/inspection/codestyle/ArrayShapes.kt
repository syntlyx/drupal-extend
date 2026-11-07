package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.Catch
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.DoWhile
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.ElseIf
import com.jetbrains.php.lang.psi.elements.For
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpEmpty
import com.jetbrains.php.lang.psi.elements.PhpIsset
import com.jetbrains.php.lang.psi.elements.PhpMatchExpression
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.PhpSwitch
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.elements.While
import com.jetbrains.php.lang.lexer.PhpTokenTypes

/**
 * Best-effort array-shape inference for generated `@param` / `@return` tags: a plain `array`
 * hint becomes `array{key: type, …}` when the function body makes the keys obvious.
 *
 * Sources, all syntactic and own-scope only (nested closures excluded):
 * - `return [...]` literals — keys and literal value types, merged across return statements
 *   (a key missing from some return renders optional: `key?:`);
 * - `return $var` where `$var` is built locally — the `$var = [...]` literal plus
 *   `$var['key'] = …` assignments (the Drupal render-array pattern);
 * - for parameters, `$param['key']` accesses — reads guarded by `??` / `isset()` / `empty()`
 *   render optional, value types stay `mixed` (a read says nothing about the type).
 *
 * Anything dynamic (computed keys, spreads, list-only arrays of mixed types) falls back to the
 * plain `array` the tag would have carried anyway — a wrong shape is worse than none.
 */
internal object ArrayShapes {

    private const val MAX_NESTING_DEPTH = 2
    private const val MAX_KEYS = 12
    private const val MAX_CALL_SITES = 8
    private const val MAX_SUPER_CLASSES = 16

    /** Shape for the `@return` of [function] (declared `: array`), or null when underivable. */
    fun returnArrayShape(function: Function): String? {
        val returns = PsiTreeUtil.findChildrenOfType(function, PhpReturn::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, Function::class.java) == function }
        if (returns.isEmpty()) return null

        val shapes = returns.map { returnShapeEntries(function, it) }
        if (shapes.all { !it.isNullOrEmpty() }) {
            renderShape(mergeShapes(shapes.filterNotNull()))?.let { return it }
        }

        return uniformListType(returns)
    }

    /**
     * Shape for an `array` [parameter], or null when underivable. Derived from `$param['key']`
     * usages in the function's own body; when the body says nothing (pass-through parameters
     * like a writer's `$values`) and [allowCallSiteSearch] is set, falls back to the call sites —
     * literal array arguments and locally built variables enumerate the keys the callers pass.
     *
     * [allowCallSiteSearch] gates the caller fallback because it runs a project-wide
     * [ReferencesSearch] (see [callerShapeEntries]): fine for a single, deliberate docblock fix,
     * but the whole-file / batch fixer must pass `false` — fanning one global search out across
     * every function in the file, on the EDT under a write lock, freezes the IDE.
     */
    fun parameterArrayShape(function: Function, parameter: Parameter, allowCallSiteSearch: Boolean): String? {
        val parameterName = parameter.name
        val guardedKeys = guardCheckedKeys(function, parameterName)
        val entries = LinkedHashMap<String, ShapeEntry>()

        for (access in PsiTreeUtil.findChildrenOfType(function, ArrayAccessExpression::class.java)) {
            if (PsiTreeUtil.getParentOfType(access, Function::class.java) != function) continue
            val receiver = access.value as? Variable ?: continue
            if (receiver.name != parameterName) continue
            val key = (access.index?.value as? StringLiteralExpression)?.contents
                ?.takeIf { it.isNotBlank() } ?: continue

            val optional = access.isGuardedRead() || key in guardedKeys
            val existing = entries[key]
            entries[key] = ShapeEntry(
                type = existing?.type ?: "mixed",
                optional = (existing?.optional ?: true) && optional,
            )
        }

        if (entries.size > MAX_KEYS) return null
        if (entries.isNotEmpty()) return renderShape(entries)
        if (!allowCallSiteSearch) return null

        return callerShapeEntries(function, parameter)?.let { renderShape(it) }
    }

    /**
     * Shape derived from the call sites of [function]: arguments at [parameter]'s position that
     * are array literals or locally built variables enumerate the keys callers actually pass.
     * Used only when the function body itself reads no keys — the body is the better witness.
     * Capped at [MAX_CALL_SITES] sites. This runs a project-wide [ReferencesSearch], so it is
     * reached only for a single, deliberate docblock fix (never the highlighting path, and never
     * the whole-file / batch fixer — see [parameterArrayShape]'s `allowCallSiteSearch`). For
     * methods, call sites are also searched through the topmost overridden declaration, so
     * interface-typed calls count for the implementation.
     */
    private fun callerShapeEntries(function: Function, parameter: Parameter): Map<String, ShapeEntry>? {
        val project = function.project
        if (DumbService.getInstance(project).isDumb) return null
        val parameterIndex = function.parameters.indexOf(parameter).takeIf { it >= 0 } ?: return null

        val shapes = mutableListOf<Map<String, ShapeEntry>>()
        val scope = GlobalSearchScope.projectScope(project)
        for (target in callerSearchTargets(function)) {
            ProgressManager.checkCanceled()
            ReferencesSearch.search(target, scope).forEach(Processor { reference ->
                ProgressManager.checkCanceled()
                callSiteShapeEntries(reference.element, parameterIndex)?.let { shapes += it }
                shapes.size < MAX_CALL_SITES
            })
            if (shapes.size >= MAX_CALL_SITES) break
        }

        if (shapes.isEmpty()) return null
        return mergeShapes(shapes).takeIf { it.isNotEmpty() && it.size <= MAX_KEYS }
    }

    private fun callSiteShapeEntries(element: PsiElement, parameterIndex: Int): Map<String, ShapeEntry>? {
        val call = element as? FunctionReference ?: return null
        return when (val argument = call.parameters.getOrNull(parameterIndex)) {
            is ArrayCreationExpression -> literalShapeEntries(argument, depth = 0)
            is Variable -> PsiTreeUtil.getParentOfType(call, Function::class.java)
                ?.let { caller -> locallyBuiltVariableShape(caller, argument.name) }

            else -> null
        }
    }

    /** The function itself plus, for methods, every super declaration of the same name. */
    private fun callerSearchTargets(function: Function): List<Function> {
        val targets = mutableListOf(function)
        val method = function as? Method ?: return targets

        val methodName = method.name
        val visited = HashSet<String>()
        val queue = ArrayDeque(method.containingClass?.supers?.toList().orEmpty())
        while (queue.isNotEmpty() && visited.size < MAX_SUPER_CLASSES) {
            ProgressManager.checkCanceled()
            val clazz = queue.removeFirst()
            if (!visited.add(clazz.fqn)) continue
            clazz.findMethodByName(methodName)
                ?.takeIf { it.containingClass?.fqn == clazz.fqn }
                ?.let { targets += it }
            queue.addAll(clazz.supers)
        }

        return targets
    }

    /**
     * Keys of [parameterName] whose presence the function checks anywhere via
     * `array_key_exists('key', $param)`, `isset($param['key'])`, or `empty($param['key'])` —
     * the check makes the key optional even when the read itself sits unguarded inside the
     * guarded branch.
     */
    private fun guardCheckedKeys(function: Function, parameterName: String?): Set<String> {
        val keys = mutableSetOf<String>()

        for (call in PsiTreeUtil.findChildrenOfType(function, FunctionReference::class.java)) {
            if (call.name != "array_key_exists") continue
            if (PsiTreeUtil.getParentOfType(call, Function::class.java) != function) continue
            val key = (call.parameters.getOrNull(0) as? StringLiteralExpression)?.contents
                ?.takeIf { it.isNotBlank() } ?: continue
            val variable = call.parameters.getOrNull(1) as? Variable ?: continue
            if (variable.name == parameterName) keys += key
        }

        val guardExpressions = PsiTreeUtil.findChildrenOfType(function, PhpIsset::class.java) +
            PsiTreeUtil.findChildrenOfType(function, PhpEmpty::class.java)
        for (guard in guardExpressions) {
            if (PsiTreeUtil.getParentOfType(guard, Function::class.java) != function) continue
            for (access in PsiTreeUtil.findChildrenOfType(guard, ArrayAccessExpression::class.java)) {
                if ((access.value as? Variable)?.name != parameterName) continue
                (access.index?.value as? StringLiteralExpression)?.contents
                    ?.takeIf { it.isNotBlank() }
                    ?.let { keys += it }
            }
        }

        return keys
    }

    // ---------------------------------------------------------------------
    // Return-statement shapes
    // ---------------------------------------------------------------------

    private fun returnShapeEntries(function: Function, phpReturn: PhpReturn): Map<String, ShapeEntry>? {
        return when (val argument = phpReturn.argument) {
            is ArrayCreationExpression -> literalShapeEntries(argument, depth = 0)
            is Variable -> locallyBuiltVariableShape(function, argument.name)
            else -> null
        }
    }

    /**
     * The shape of a variable built in [function]'s own scope: its `$var = [...]` literal
     * initializer (when present) extended by every `$var['key'] = …` assignment.
     */
    private fun locallyBuiltVariableShape(function: Function, variableName: String?): Map<String, ShapeEntry>? {
        if (variableName.isNullOrBlank()) return null

        val entries = LinkedHashMap<String, ShapeEntry>()
        var sawInitializerOrWrite = false

        for (assignment in PsiTreeUtil.findChildrenOfType(function, AssignmentExpression::class.java)) {
            if (PsiTreeUtil.getParentOfType(assignment, Function::class.java) != function) continue

            val target = assignment.variable
            if (target is Variable && target.name == variableName) {
                val literal = assignment.value as? ArrayCreationExpression ?: continue
                val literalEntries = literalShapeEntries(literal, depth = 0) ?: return null
                entries.putAll(literalEntries)
                sawInitializerOrWrite = true
                continue
            }

            if (target is ArrayAccessExpression && (target.value as? Variable)?.name == variableName) {
                val key = (target.index?.value as? StringLiteralExpression)?.contents
                    ?.takeIf { it.isNotBlank() }
                    ?: continue
                val valueExpression = (assignment.value as? PhpPsiElement)?.unwrapArrayValue()
                // A write inside an if/loop/switch branch may not happen — the key is optional.
                entries[key] = ShapeEntry(
                    valueType(valueExpression, depth = 1),
                    optional = assignment.isConditionallyExecuted(function),
                )
                sawInitializerOrWrite = true
            }
        }

        if (!sawInitializerOrWrite || entries.isEmpty() || entries.size > MAX_KEYS) return null
        return entries
    }

    // ---------------------------------------------------------------------
    // Literal inference
    // ---------------------------------------------------------------------

    private fun literalShapeEntries(literal: ArrayCreationExpression, depth: Int): Map<String, ShapeEntry>? {
        val entries = LinkedHashMap<String, ShapeEntry>()
        for (hashElement in literal.hashElements) {
            val key = (hashElement.key as? StringLiteralExpression)?.contents
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val valueExpression = hashElement.value?.unwrapArrayValue()
            entries[key] = ShapeEntry(valueType(valueExpression, depth + 1), optional = false)
        }

        if (entries.isEmpty() || entries.size > MAX_KEYS) return null
        // Mixed hash + list entries (some values without keys) — not a clean shape.
        if (literal.listValueExpressions().isNotEmpty()) return null
        return entries
    }

    private fun valueType(expression: PsiElement?, depth: Int): String {
        return when {
            expression == null -> "mixed"
            expression is StringLiteralExpression -> "string"
            expression.node?.elementType == PhpElementTypes.NUMBER ->
                if (expression.text.any { it == '.' || it == 'e' || it == 'E' }) "float" else "int"

            expression is ConstantReference -> when (expression.name?.lowercase()) {
                "true", "false" -> "bool"
                "null" -> "null"
                else -> "mixed"
            }

            expression is NewExpression ->
                expression.classReference?.fqn?.takeIf { it.startsWith("\\") } ?: "mixed"

            expression is ArrayCreationExpression -> nestedArrayType(expression, depth)

            expression is UnaryExpression && expression.operation?.node?.elementType == PhpTokenTypes.opMINUS ->
                valueType(expression.value, depth).takeIf { it == "int" || it == "float" } ?: "mixed"

            else -> "mixed"
        }
    }

    private fun nestedArrayType(literal: ArrayCreationExpression, depth: Int): String {
        if (depth >= MAX_NESTING_DEPTH) return "array"

        literalShapeEntries(literal, depth)?.let { entries ->
            renderShape(entries)?.let { return it }
        }

        // A keyless list of uniform literal types renders as `type[]`.
        val listValues = literal.listValueExpressions()
        if (listValues.isNotEmpty() && !literal.hashElements.iterator().hasNext()) {
            val elementTypes = listValues.map { valueType(it, depth + 1) }.distinct()
            val single = elementTypes.singleOrNull()
            if (single != null && single != "mixed" && single != "null" && !single.contains('{')) {
                return "$single[]"
            }
        }

        return "array"
    }

    /** The unwrapped value expressions of keyless (list) entries of this array literal. */
    private fun ArrayCreationExpression.listValueExpressions(): List<PsiElement> {
        return children
            .filterIsInstance<PhpPsiElement>()
            .filter { it !is com.jetbrains.php.lang.psi.elements.ArrayHashElement }
            .filter { it.node?.elementType == PhpElementTypes.ARRAY_VALUE }
            .mapNotNull { it.unwrapArrayValue() }
    }

    /** Array hash values arrive wrapped in an `Array value` composite — unwrap to the expression. */
    private fun PhpPsiElement.unwrapArrayValue(): PsiElement? {
        if (node?.elementType == PhpElementTypes.ARRAY_VALUE) return firstPsiChild
        return this
    }

    // ---------------------------------------------------------------------
    // Merging and rendering
    // ---------------------------------------------------------------------

    private data class ShapeEntry(val type: String, val optional: Boolean)

    /** Keys missing from some return statement become optional; conflicting types widen to mixed. */
    private fun mergeShapes(shapes: List<Map<String, ShapeEntry>>): Map<String, ShapeEntry> {
        val merged = LinkedHashMap<String, ShapeEntry>()
        val allKeys = shapes.flatMap { it.keys }.distinct()
        for (key in allKeys) {
            val occurrences = shapes.mapNotNull { it[key] }
            val types = occurrences.map { it.type }.distinct()
            merged[key] = ShapeEntry(
                type = types.singleOrNull() ?: "mixed",
                optional = occurrences.size < shapes.size || occurrences.any { it.optional },
            )
        }
        return merged
    }

    private fun renderShape(entries: Map<String, ShapeEntry>): String? {
        if (entries.isEmpty() || entries.size > MAX_KEYS) return null

        val rendered = entries.entries.joinToString(", ") { (key, entry) ->
            val safeKey = if (key.matches(Regex("""[A-Za-z_]\w*"""))) key else "'$key'"
            val marker = if (entry.optional) "?" else ""
            "$safeKey$marker: ${entry.type}"
        }

        return "array{$rendered}"
    }

    /** `return` statements that all yield a uniform-type literal list render as `type[]`. */
    private fun uniformListType(returns: List<PhpReturn>): String? {
        val elementTypes = returns.map { phpReturn ->
            val literal = phpReturn.argument as? ArrayCreationExpression ?: return null
            if (literal.hashElements.iterator().hasNext()) return null
            val values = literal.listValueExpressions()
            if (values.isEmpty()) return null
            values.map { valueType(it, depth = 1) }.distinct().singleOrNull() ?: return null
        }.distinct()

        val single = elementTypes.singleOrNull() ?: return null
        if (single == "mixed" || single == "null" || single.contains('{')) return null
        return "$single[]"
    }

    /**
     * Re-renders an inline `array{…}` shape across multiple docblock lines, one top-level entry
     * per line (the canonical form the docblock rules already normalize around):
     * ```
     * array{
     *   access_token: string,
     *   refresh_token: string
     * }
     * ```
     * Nested shapes stay inline within their entry. [indentation] is the docblock's file
     * indentation; the caller appends the result right after `@param ` / `@return `.
     */
    fun toMultiline(inlineShape: String, indentation: String): String {
        val inner = inlineShape.removePrefix("array{").removeSuffix("}")
        val items = splitTopLevelEntries(inner)
        if (items.isEmpty()) return inlineShape

        return buildString {
            append("array{")
            items.forEachIndexed { index, item ->
                append("\n").append(indentation).append(" *   ").append(item.trim())
                if (index < items.lastIndex) append(",")
            }
            append("\n").append(indentation).append(" * }")
        }
    }

    private fun splitTopLevelEntries(inner: String): List<String> {
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (char in inner) {
            when (char) {
                '{', '<', '[', '(' -> depth++
                '}', '>', ']', ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) {
                    items.add(current.toString())
                    current.clear()
                    continue
                }
            }
            current.append(char)
        }
        if (current.isNotBlank()) items.add(current.toString())
        return items
    }

    /** True when [scope] may skip this element: an if/loop/switch/ternary/catch branch encloses it. */
    private fun PsiElement.isConditionallyExecuted(scope: Function): Boolean {
        var node: PsiElement? = parent
        while (node != null && node != scope) {
            when (node) {
                is If, is ElseIf, is Else, is PhpSwitch, is PhpMatchExpression, is TernaryExpression,
                is While, is DoWhile, is For, is ForeachStatement, is Catch -> return true
            }
            node = node.parent
        }
        return false
    }

    /** True when this read is guarded (`?? default`, `isset()`, `empty()`) — the key is optional. */
    private fun ArrayAccessExpression.isGuardedRead(): Boolean {
        var node: PsiElement? = this
        var hops = 0
        while (node != null && hops < 4) {
            val parent = node.parent
            when {
                parent is PhpIsset || parent is PhpEmpty -> return true
                parent is BinaryExpression
                    && parent.operationType == PhpTokenTypes.opCOALESCE
                    && parent.leftOperand == node -> return true
            }
            node = parent
            hops++
        }
        return false
    }
}
