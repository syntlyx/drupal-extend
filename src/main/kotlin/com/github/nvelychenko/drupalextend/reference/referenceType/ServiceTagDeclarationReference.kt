package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.ServiceTags
import com.github.nvelychenko.drupalextend.util.Services
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult

/**
 * Navigates from a tag declaration (`tags: - { name: '<tag>' }`) to where the tag is collected:
 * the `!tagged_iterator` / `!tagged_locator` argument or `service_collector` tag of its
 * consumers. The services carrying the tag are deliberately not targets — listing them is what
 * the consumer-side [ServiceTagReference] already does.
 */
class ServiceTagDeclarationReference(
    element: PsiElement,
    private val tagName: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project

    override fun getCanonicalText(): String = tagName

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Services.isSupportEnabled(project)) return emptyArray()

        return ServiceTags.tagConsumerElements(project, tagName)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }
}
