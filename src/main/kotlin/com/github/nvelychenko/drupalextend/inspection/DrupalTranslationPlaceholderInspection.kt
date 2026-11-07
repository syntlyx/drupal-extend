package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Validates placeholder/argument agreement in Drupal translation calls.
 *
 * Covered, conservatively, only when the translated string is a plain string literal and the
 * arguments are an inline array literal:
 *  - `t('...', [...])`
 *  - `$this->t('...', [...])` (StringTranslationTrait — matched by method name + literal first arg)
 *  - `new TranslatableMarkup('...', [...])`
 *  - `\Drupal::translation()->translate('...', [...])`
 *  - `formatPlural($count, 'singular', 'plural', [...])` (both string args checked)
 *
 * Checks:
 *  1. Placeholder in the string with no matching args key            -> WARNING.
 *  2. Args key never used in the string(s)                           -> WEAK_WARNING.
 *  3. Args key without a valid `@` / `%` / `:` prefix                -> WEAK_WARNING.
 *
 * Matching mirrors FormattableMarkup's `strtr()`: keys replace literal substrings, so
 * `%payoff` satisfies `%payoff.` when a sentence continues right after the placeholder —
 * a scanned token counts as covered when some key occurs at its start offset, and a key
 * counts as used when it occurs anywhere in a scanned string.
 *
 * `@count` in `formatPlural` is supplied automatically and is never reported either way.
 *
 * Hot-path discipline: pure PSI, no index access, one hoisted regex. Every element bails on a
 * name mismatch before any allocation.
 */
class DrupalTranslationPlaceholderInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!project.drupalExtendSettings.isEnabled) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is FunctionReference -> visitCall(element, holder)
                    is NewExpression -> visitNew(element, holder)
                }
            }
        }
    }

    private fun visitCall(reference: FunctionReference, holder: ProblemsHolder) {
        val name = reference.name ?: return
        when (name) {
            "t", "translate" -> {
                val parameters = reference.parameterList?.parameters ?: return
                val string = parameters.getOrNull(0) as? StringLiteralExpression ?: return
                val array = parameters.getOrNull(1) as? ArrayCreationExpression ?: return
                check(holder, listOf(string), array, plural = false)
            }
            "formatPlural" -> {
                // Only on instance/static calls (TranslationManager / StringTranslationTrait),
                // never a same-named global function.
                if (reference !is MethodReference) return
                val parameters = reference.parameterList?.parameters ?: return
                val singular = parameters.getOrNull(1) as? StringLiteralExpression ?: return
                val plural = parameters.getOrNull(2) as? StringLiteralExpression ?: return
                val array = parameters.getOrNull(3) as? ArrayCreationExpression ?: return
                check(holder, listOf(singular, plural), array, plural = true)
            }
        }
    }

    private fun visitNew(expression: NewExpression, holder: ProblemsHolder) {
        val classReference = expression.classReference ?: return
        if (classReference.name != "TranslatableMarkup") return
        val parameters = expression.parameterList?.parameters ?: return
        val string = parameters.getOrNull(0) as? StringLiteralExpression ?: return
        val array = parameters.getOrNull(1) as? ArrayCreationExpression ?: return
        check(holder, listOf(string), array, plural = false)
    }

    /**
     * @param strings the literal string arguments to scan for placeholders (1 for `t`, 2 for
     *  `formatPlural`). A literal here is guaranteed by the caller.
     * @param plural true for `formatPlural`, where `@count` is provided automatically.
     */
    private fun check(
        holder: ProblemsHolder,
        strings: List<StringLiteralExpression>,
        array: ArrayCreationExpression,
        plural: Boolean,
    ) {
        // Interpolated literals ("$foo") aren't safe to scan; treat them as opaque.
        val literalStrings = strings.filter { it.firstPsiChild == null }
        if (literalStrings.isEmpty()) return

        // An open-ended array (spread, variable/expression key, value-only entry, or interpolated
        // key) means we can't see every key, so all three checks would be unsound.
        val keyClosed = array.hashElements.all {
            val key = it.key
            key is StringLiteralExpression && key.firstPsiChild == null
        }
        if (!keyClosed) return

        val literalKeyElements = array.hashElements
            .map { it.key }
            .filterIsInstance<StringLiteralExpression>()
        val keys = literalKeyElements.map { it.contents }.toSet()

        // Check 1: placeholder used but no matching key. Replacement is strtr(), so the scanned
        // token may run past the real placeholder into sentence punctuation ("$%payoff. The…");
        // a token is covered when any key occurs at its start offset, not only on exact equality.
        // `@count` is auto-provided for formatPlural.
        val effectiveKeys = if (plural) keys + COUNT_PLACEHOLDER else keys
        for (string in literalStrings) {
            val contents = string.contents
            for (match in PLACEHOLDER.findAll(contents)) {
                if (effectiveKeys.any { contents.startsWith(it, match.range.first) }) continue
                holder.registerProblem(
                    string,
                    "Placeholder '${match.value.trimEnd('.', '-')}' has no replacement argument",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }

        for (keyElement in literalKeyElements) {
            val key = keyElement.contents
            // formatPlural supplies @count automatically; never flag it as unused.
            if (plural && key == COUNT_PLACEHOLDER) continue

            val prefix = key.firstOrNull()
            if (prefix != '@' && prefix != '%' && prefix != ':') {
                holder.registerProblem(
                    keyElement,
                    "Replacement argument '$key' must start with @, % or :",
                    ProblemHighlightType.WEAK_WARNING,
                )
                continue
            }

            // Check 2: same strtr() semantics — the key is used iff it occurs literally somewhere
            // in a scanned string, even inside a longer token like `%payoff.`.
            if (literalStrings.none { it.contents.contains(key) }) {
                holder.registerProblem(
                    keyElement,
                    "Replacement argument '$key' is not used in the string",
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }

    private companion object {
        // token = prefix (@ % :) + [A-Za-z0-9_.-]+
        val PLACEHOLDER = Regex("[@%:][A-Za-z0-9_.-]+")
        const val COUNT_PLACEHOLDER = "@count"
    }
}
