package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.php.PhpIndex
import org.jetbrains.yaml.psi.YAMLScalar

class ServiceClassReference(element: YAMLScalar, private val classFqn: String) :
    PsiPolyVariantReferenceBase<YAMLScalar>(element) {

    private val project = element.project

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Services.isSupportEnabled(project)) return emptyArray()

        return PhpIndex.getInstance(project)
            .getAnyByFQN(classFqn.normalizeServiceClassFqn())
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }
}
