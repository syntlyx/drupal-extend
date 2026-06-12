package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.ServiceTags
import com.github.nvelychenko.drupalextend.util.Services
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult

/**
 * Navigates from a tag declaration (`tags: - { name: '<tag>' }`) to where the tag is used: the
 * `!tagged_iterator` / `!tagged_locator` argument that collects it, plus the sibling services
 * carrying the same tag. The declaring service itself is excluded from the targets.
 */
class ServiceTagDeclarationReference(
    element: PsiElement,
    private val tagName: String,
    private val declaringServiceId: String?,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project

    override fun getCanonicalText(): String = tagName

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Services.isSupportEnabled(project)) return emptyArray()

        return ServiceTags.tagUsageElements(project, tagName, declaringServiceId)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }
}
