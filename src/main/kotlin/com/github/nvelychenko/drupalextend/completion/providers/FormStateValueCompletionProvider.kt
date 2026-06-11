package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Completes form-state value keys for `$form_state->getValue('…')` (and `hasValue`/`setValue`,
 * which share the same key space). The candidate keys are the string indexes written into a
 * `$form` array across the class's own methods — i.e. `$form['some_key'] = …`.
 *
 * Performance: the literal pattern this is registered against matches every string method
 * parameter, so the receiver/method gates run first and bail O(1). The per-class key collection
 * is cached (keyed on the containing file) so it is computed once, not on every keystroke.
 */
class FormStateValueCompletionProvider : CompletionProvider<CompletionParameters>() {

    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: completionParameters.position
        val project = leaf.project
        if (!project.drupalExtendSettings.isEnabled) return
        if (DumbService.isDumb(project)) return

        val literal = leaf.parent as? StringLiteralExpression ?: return
        val parameterList = literal.parent as? ParameterList ?: return
        if (parameterList.parameters.firstOrNull()?.isEquivalentTo(literal) != true) return

        val methodReference = parameterList.parent as? MethodReference ?: return
        if (methodReference.name !in FORM_STATE_VALUE_METHODS) return

        // Receiver gate without resolving: the variable name must look like a form state object.
        val receiver = methodReference.classReference as? Variable ?: return
        val receiverName = receiver.name
        if (FORM_STATE_HINTS.none { receiverName.contains(it, ignoreCase = false) }) return

        for (key in formKeys(literal)) {
            ProgressManager.checkCanceled()
            completionResultSet.addElement(LookupElementBuilder.create(key))
        }
    }

    /**
     * Form keys for the position: the containing class's `$form[...]` keys when inside a class,
     * otherwise just the containing function (procedural form callbacks). Class-level results are
     * cached on the [PhpClass]; the function fallback is cheap and scoped, so it runs inline.
     */
    private fun formKeys(literal: StringLiteralExpression): Set<String> {
        val phpClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java)
        if (phpClass != null) {
            return CachedValuesManager.getCachedValue(phpClass) {
                CachedValueProvider.Result.create(collectFormKeys(phpClass), phpClass.containingFile)
            }
        }

        val function = PsiTreeUtil.getParentOfType(literal, Function::class.java) ?: return emptySet()
        return collectFormKeys(function)
    }

    /**
     * Collect every string index written into a `$form` variable in [scope] — both
     * `$form['a'] = …` and the first index of `$form['a']['b'] = …`.
     */
    private fun collectFormKeys(scope: PsiElement): Set<String> {
        val keys = LinkedHashSet<String>()
        for (assignment in PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression::class.java)) {
            ProgressManager.checkCanceled()
            val target = assignment.variable as? ArrayAccessExpression ?: continue
            val key = formArrayFirstKey(target) ?: continue
            keys.add(key)
        }
        return keys
    }

    /**
     * For an array-access target, walk down to the innermost `$form` base and return the string
     * index applied directly to it (the first/top-level form key).
     */
    private fun formArrayFirstKey(access: ArrayAccessExpression): String? {
        var current = access
        var hops = 0
        while (hops < MAX_INDEX_HOPS) {
            when (val base = current.value) {
                is Variable -> {
                    if (base.name != FORM_VARIABLE) return null
                    return (current.index?.value as? StringLiteralExpression)?.contents
                }

                is ArrayAccessExpression -> {
                    current = base
                    hops++
                }

                else -> return null
            }
        }
        return null
    }

    private companion object {
        const val FORM_VARIABLE = "form"
        const val MAX_INDEX_HOPS = 8

        val FORM_STATE_VALUE_METHODS = setOf("getValue", "getValues", "hasValue", "setValue")
        val FORM_STATE_HINTS = listOf("form_state", "formState")
    }
}
