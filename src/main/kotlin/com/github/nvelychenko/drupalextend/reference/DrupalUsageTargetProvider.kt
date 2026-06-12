package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.reference.referenceType.LinkReference
import com.github.nvelychenko.drupalextend.reference.referenceType.PermissionsReference
import com.github.nvelychenko.drupalextend.reference.referenceType.RouteReference
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceReference
import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetProvider
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class DrupalUsageTargetProvider : UsageTargetProvider {
    override fun getTargets(element: PsiElement): Array<UsageTarget> {
        val targetElements = DrupalUsageTargets.searchTargets(element).toList()
        val targets = Array<UsageTarget>(targetElements.size) { index ->
            PsiElement2UsageTargetAdapter(targetElements[index], true)
        }

        return if (targets.isEmpty()) emptyArray() else targets
    }

    override fun getTargets(editor: Editor, file: PsiFile): Array<UsageTarget>? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return getTargets(element).takeIf { it.isNotEmpty() }
    }
}

internal object DrupalUsageTargets {
    fun searchTargets(element: PsiElement): Collection<PsiElement> {
        declarationTarget(element)?.let { return listOf(it) }

        return candidateElements(element)
            .asSequence()
            .flatMap { it.references.asSequence() }
            .filter(::isDrupalReference)
            .flatMap { it.multiResolveTargets().asSequence() }
            .distinct()
            .toList()
    }

    private fun declarationTarget(element: PsiElement): PsiElement? {
        ServicesYaml.tagNameDeclarationScalar(element)?.let { return it }

        val definition = ServicesYaml.serviceDefinitionFromKeyElement(element)
            ?: RoutingYaml.routeDefinitionFromKeyElement(element)
            ?: RoutingYaml.linkDefinitionFromKeyElement(element)
            ?: Permissions.permissionDefinitionFromKeyElement(element)
            ?: return null

        return definition.key ?: definition
    }

    private fun candidateElements(element: PsiElement): Collection<PsiElement> {
        val candidates = linkedSetOf<PsiElement>()
        candidates.add(element)
        element.parent?.let(candidates::add)
        PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java, false)?.let(candidates::add)
        PsiTreeUtil.getParentOfType(element, YAMLKeyValue::class.java, false)?.let(candidates::add)
        PsiTreeUtil.getParentOfType(element, StringLiteralExpression::class.java, false)?.let(candidates::add)
        return candidates
    }

    private fun isDrupalReference(reference: PsiReference): Boolean {
        return reference is ServiceReference ||
                reference is RouteReference ||
                reference is LinkReference ||
                reference is PermissionsReference
    }

    private fun PsiReference.multiResolveTargets(): Collection<PsiElement> {
        return if (this is PsiPolyVariantReference) {
            multiResolve(false).mapNotNull(ResolveResult::getElement)
        } else {
            listOfNotNull(resolve())
        }
    }
}
