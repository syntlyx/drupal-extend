package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.ConfigObjectReference
import com.github.nvelychenko.drupalextend.util.Configs
import com.github.nvelychenko.drupalextend.util.getServiceMethodReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ConfigObjectReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        // Pure-PSI gates first; the settings service lookup runs only for literals that are
        // actually in a config-name position (this provider sees every string literal).
        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        if (literal.contents.isEmpty()) return PsiReference.EMPTY_ARRAY

        if (!isConfigNameArgument(literal) && !Configs.isSettingsConfigOverrideName(literal)) {
            return PsiReference.EMPTY_ARRAY
        }

        if (!Configs.isSupportEnabled(literal.project)) return PsiReference.EMPTY_ARRAY

        return arrayOf(ConfigObjectReference(literal, literal.contents, literal.contentRange()))
    }

    /** O(1) parent-type bail: only the first string argument of a config-name call. */
    private fun isConfigNameArgument(literal: StringLiteralExpression): Boolean {
        val methodReference = literal.getServiceMethodReference() ?: return false

        return Configs.isConfigNameCall(methodReference)
    }
}
