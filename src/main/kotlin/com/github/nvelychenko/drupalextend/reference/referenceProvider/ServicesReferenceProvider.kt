package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceReference
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.getServiceMethodReference
import com.github.nvelychenko.drupalextend.util.isContainerGetCall
import com.github.nvelychenko.drupalextend.util.isDrupalServiceCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ServicesReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val project = element.project

        if (!Services.isSupportEnabled(project)) return PsiReference.EMPTY_ARRAY

        element as StringLiteralExpression
        if (element.contents.isEmpty()) return PsiReference.EMPTY_ARRAY

        val methodReference = element.getServiceMethodReference() ?: return PsiReference.EMPTY_ARRAY
        if (!methodReference.isDrupalServiceCall() && !methodReference.isContainerGetCall()) {
            return PsiReference.EMPTY_ARRAY
        }

        return arrayOf(ServiceReference(element, element.contents, element.contentRange()))
    }
}
