package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.FormCallbackReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.PhpReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Turns a Form API callback string literal into a reference to the method or function it names.
 *
 * Registered against a broad string-literal pattern, so the very first checks must be cheap and
 * bail O(1) for the overwhelming majority of literals that are not callbacks. We classify the
 * literal by its own text and immediate parent shape, then take a *bounded* number of parent hops
 * (no subtree scans) to confirm it sits under one of the [FORM_CALLBACK_KEYS].
 */
class FormCallbackReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        // Cast + shape gates first: highlighting feeds every string literal in the file through
        // here, so the settings service lookup happens only for literals that pass the cheap
        // pure-PSI checks.
        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        val contents = literal.contents
        if (contents.isEmpty()) return PsiReference.EMPTY_ARRAY

        // Cheap parent-shape gate: a callback string is always either an array value (its parent
        // chain leads into an ArrayCreationExpression) or the RHS of an `$x[...][] = '...'` assign.
        // Everything else (the common case) is rejected before any walking.
        if (!underCallbackKey(literal)) return PsiReference.EMPTY_ARRAY

        if (!literal.project.drupalExtendSettings.isEnabled) return PsiReference.EMPTY_ARRAY

        val reference = buildReference(literal, contents) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(reference)
    }

    /**
     * Bounded walk: confirm the literal sits as a value under one of the form-callback keys.
     * Covers `'#submit' => ['::m']`, `'#ajax' => ['callback' => '::m']`, and
     * `$form['#submit'][] = '::m'`. At most a handful of parent hops, no tree scans.
     */
    private fun underCallbackKey(literal: StringLiteralExpression): Boolean {
        // Shape A/B: literal is a value inside an array creation — at most three hops away
        // (value wrapper, optional hash element, array). A bounded climb instead of
        // getParentOfType, which would walk to the file root for every ordinary literal.
        val arrayValue = literal.parent
        val containingArray = boundedEnclosingArrayCreation(literal)
        if (containingArray != null) {
            return arrayUnderCallbackKey(containingArray)
        }

        // Shape C: `$form['#submit'][] = '::m'` — RHS of an assignment whose target indexes a
        // callback key.
        val assignment = arrayValue as? AssignmentExpression
            ?: literal.parent.parent as? AssignmentExpression
        if (assignment != null && assignment.value?.isEquivalentTo(literal) == true) {
            return assignmentTargetIndexesCallbackKey(assignment)
        }

        return false
    }

    private fun boundedEnclosingArrayCreation(literal: StringLiteralExpression): ArrayCreationExpression? {
        var node: PsiElement? = literal.parent
        repeat(3) {
            when (node) {
                is ArrayCreationExpression -> return node
                null -> return null
                else -> node = node?.parent
            }
        }

        return node as? ArrayCreationExpression
    }

    /**
     * The array directly holding the callback value must itself be the value of a hash element
     * whose key is a form-callback key. For `#ajax`, the value array is keyed by `callback`, and
     * that array is in turn the value under the `#ajax` key — one extra hop, still bounded.
     */
    private fun arrayUnderCallbackKey(array: ArrayCreationExpression): Boolean {
        val outerHash = PsiTreeUtil.getParentOfType(array, ArrayHashElement::class.java)
        if (outerHash == null) {
            // No enclosing hash: the array may be assigned directly, e.g.
            // `$form['#submit'] = ['::m']` or `$form['#submit'][] = ['::m']`.
            val assignment = PsiTreeUtil.getParentOfType(array, AssignmentExpression::class.java) ?: return false
            return assignment.value?.isEquivalentTo(array) == true &&
                    assignmentTargetIndexesCallbackKey(assignment)
        }
        val key = (outerHash.key as? StringLiteralExpression)?.contents ?: return false

        if (key in FORM_CALLBACK_KEYS) return true

        // `'#ajax' => ['callback' => '::m']`: the literal lives under the `callback` key, whose
        // host array is the value of the `#ajax` hash.
        if (key == AJAX_CALLBACK_KEY) {
            val ajaxArray = PsiTreeUtil.getParentOfType(outerHash, ArrayCreationExpression::class.java)
                ?: return false
            val ajaxHash = PsiTreeUtil.getParentOfType(ajaxArray, ArrayHashElement::class.java)
                ?: return false
            return (ajaxHash.key as? StringLiteralExpression)?.contents == AJAX_KEY
        }

        return false
    }

    private fun assignmentTargetIndexesCallbackKey(assignment: AssignmentExpression): Boolean {
        var access = assignment.variable as? ArrayAccessExpression ?: return false
        // Walk the (bounded) chain of `[...]` accesses looking for a callback-key string index.
        var hops = 0
        while (hops < MAX_INDEX_HOPS) {
            val index = (access.index?.value as? StringLiteralExpression)?.contents
            if (index != null && index in FORM_CALLBACK_KEYS) return true
            access = access.value as? ArrayAccessExpression ?: return false
            hops++
        }
        return false
    }

    private fun buildReference(literal: StringLiteralExpression, contents: String): FormCallbackReference? {
        // `'::method'` → own method of the containing class.
        if (contents.startsWith(CALLABLE_PREFIX)) {
            val methodName = contents.substring(CALLABLE_PREFIX.length)
            if (methodName.isEmpty()) return null
            val phpClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java) ?: return null
            return FormCallbackReference(literal, FormCallbackReference.Kind.OWN_METHOD, methodName, phpClass)
        }

        // `[Foo::class, 'method']` / `[$this, 'method']` → method of the named class.
        classMethodReference(literal, contents)?.let { return it }

        // Plain `function_name` → global function, but only if the index actually knows it.
        if (PhpIndex.getInstance(literal.project).getFunctionsByName(contents).isNotEmpty()) {
            return FormCallbackReference(literal, FormCallbackReference.Kind.GLOBAL_FUNCTION, contents, null)
        }

        return null
    }

    /**
     * Handles the `[$class, 'method']` callable shape: the literal must be the second value of a
     * two-element array literal whose first value is `Foo::class` or `$this`.
     */
    private fun classMethodReference(literal: StringLiteralExpression, contents: String): FormCallbackReference? {
        val array = literal.parent?.parent as? ArrayCreationExpression ?: return null
        val values = PsiTreeUtil.getChildrenOfType(array, PhpPsiElement::class.java)
            ?.filter { it !is ArrayHashElement }
            ?: return null
        if (values.size != 2) return null
        // The literal must be the second array value.
        if (values[1].firstChild?.isEquivalentTo(literal) != true) return null

        return when (val first = values[0].firstChild) {
            is ClassConstantReference -> {
                if (first.name != CLASS_CONSTANT) return null
                val phpClass = (first.classReference as? PhpReference)?.resolve() as? PhpClass ?: return null
                FormCallbackReference(literal, FormCallbackReference.Kind.CLASS_METHOD, contents, phpClass)
            }

            is Variable -> {
                if (first.name != THIS_VARIABLE) return null
                val phpClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java) ?: return null
                FormCallbackReference(literal, FormCallbackReference.Kind.OWN_METHOD, contents, phpClass)
            }

            else -> null
        }
    }

    private companion object {
        const val CALLABLE_PREFIX = "::"
        const val CLASS_CONSTANT = "class"
        const val THIS_VARIABLE = "this"
        const val AJAX_KEY = "#ajax"
        const val AJAX_CALLBACK_KEY = "callback"
        const val MAX_INDEX_HOPS = 6

        val FORM_CALLBACK_KEYS = setOf(
            "#submit",
            "#validate",
            "#element_validate",
            "#after_build",
            "#pre_render",
            "#process",
            "#ajax",
        )
    }
}
