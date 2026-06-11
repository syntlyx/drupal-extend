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

        // Per-string placeholder lists, so a missing one is reported on the string that uses it.
        val placeholdersByString = literalStrings.map { string ->
            string to PLACEHOLDER.findAll(string.contents).map { it.value }.toList()
        }
        val placeholders = placeholdersByString.flatMap { it.second }.toSet()

        val literalKeyElements = array.hashElements
            .map { it.key }
            .filterIsInstance<StringLiteralExpression>()
            .filter { it.firstPsiChild == null }
        // An open-ended array (spread, variable/expression key, value-only entry, or interpolated
        // key) means we can't see every key, so "unused" and "bad prefix" would be unsound.
        val keyClosed = array.hashElements.all {
            val key = it.key
            key is StringLiteralExpression && key.firstPsiChild == null
        }
        val keys = literalKeyElements.map { it.contents }.toSet()

        // Check 1: placeholder used but no matching key. Skip when the array is open-ended (a
        // hidden key might satisfy it). `@count` is auto-provided for formatPlural.
        if (keyClosed) {
            for ((string, used) in placeholdersByString) {
                for (placeholder in used) {
                    if (plural && placeholder == COUNT_PLACEHOLDER) continue
                    if (placeholder in keys) continue
                    holder.registerProblem(
                        string,
                        "Placeholder '$placeholder' has no replacement argument",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }

        // Checks 2 & 3 need to see every key, so skip the whole array if any key is non-literal.
        if (!keyClosed) return

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

            if (key !in placeholders) {
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
