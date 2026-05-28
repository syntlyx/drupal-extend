package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.RouteReference
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.getRouteMethodReference
import com.github.nvelychenko.drupalextend.util.isRouteCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class RoutesReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val project = element.project

        if (!Routes.isSupportEnabled(project)) return PsiReference.EMPTY_ARRAY

        element as StringLiteralExpression
        if (element.contents.isEmpty()) return PsiReference.EMPTY_ARRAY

        val methodReference = element.getRouteMethodReference() ?: return PsiReference.EMPTY_ARRAY
        if (!methodReference.isRouteCall(project)) return PsiReference.EMPTY_ARRAY

        return arrayOf(RouteReference(element, element.contents, element.contentRange()))
    }
}
