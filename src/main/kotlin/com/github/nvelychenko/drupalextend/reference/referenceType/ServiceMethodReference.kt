package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.php.PhpIndex

class ServiceMethodReference(
    element: PsiElement,
    private val factoryMethod: ServicesYaml.ServiceFactoryMethod,
) : PsiPolyVariantReferenceBase<PsiElement>(element, factoryMethod.methodTextRange) {

    private val project = element.project

    override fun getCanonicalText(): String {
        return factoryMethod.methodName
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Services.isSupportEnabled(project)) return emptyArray()

        val ownerClass = Services.factoryOwnerClassName(project, factoryMethod)
            ?.normalizeServiceClassFqn()
            ?: return emptyArray()
        val method = PhpIndex.getInstance(project)
            .getClassesByFQN(ownerClass)
            .firstOrNull()
            ?.findMethodByName(factoryMethod.methodName)
            ?: return emptyArray()

        return PsiElementResolveResult.createResults(method)
    }
}
