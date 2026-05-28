package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.github.nvelychenko.drupalextend.util.normalizeRouteClassFqn
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.php.PhpIndex

class RouteHandlerReference(
    element: PsiElement,
    private val handler: RoutingYaml.RouteHandler,
    rangeInElement: TextRange,
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Routes.isSupportEnabled(project)) return emptyArray()

        val phpClass = PhpIndex.getInstance(project)
            .getClassesByFQN(handler.className.normalizeRouteClassFqn())
            .firstOrNull()
            ?: return emptyArray()

        val target = handler.methodName
            ?.let { phpClass.findMethodByName(it) }
            ?: phpClass

        return PsiElementResolveResult.createResults(target)
    }
}
