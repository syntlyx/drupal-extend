package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceReference
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.isAutowireServiceArgument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Navigation from `#[Autowire(service: '...')]` to the service definition. */
class AutowireServiceReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val project = element.project

        if (!Services.isSupportEnabled(project)) return PsiReference.EMPTY_ARRAY

        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        if (literal.contents.isEmpty()) return PsiReference.EMPTY_ARRAY
        if (!literal.isAutowireServiceArgument()) return PsiReference.EMPTY_ARRAY

        return arrayOf(ServiceReference(literal, literal.contents, literal.contentRange()))
    }
}
