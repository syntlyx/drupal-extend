package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.ConfigObjectReference
import com.github.nvelychenko.drupalextend.util.Bundles
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Navigation from a bundle id in a bundle-value position to its bundle config YAML. */
class BundleValueReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        if (literal.contents.isEmpty()) return PsiReference.EMPTY_ARRAY
        if (DumbService.isDumb(literal.project)) return PsiReference.EMPTY_ARRAY

        val entityTypeId = Bundles.bundleValueEntityType(literal) ?: return PsiReference.EMPTY_ARRAY
        val configName = Bundles.bundleConfigName(entityTypeId, literal.contents)
            ?: return PsiReference.EMPTY_ARRAY

        return arrayOf(ConfigObjectReference(literal, configName, literal.contentRange()))
    }
}
