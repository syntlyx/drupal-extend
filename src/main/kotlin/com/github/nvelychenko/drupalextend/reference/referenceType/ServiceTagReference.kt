package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.ServiceTags
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult

/**
 * Navigates from a `!tagged_iterator` / `!tagged_locator` tag name to every service definition
 * carrying that tag (project + core/contrib), resolving multi-target via [ServiceTags].
 */
class ServiceTagReference(
    element: PsiElement,
    private val tagName: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project

    override fun getCanonicalText(): String = tagName

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Services.isSupportEnabled(project)) return emptyArray()

        return ServiceTags.taggedServiceKeyElements(project, tagName)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }

    /**
     * A consumer position is a *usage* of every same-named tag declaration — this is what makes
     * Show Usages / find usages on a `tags: - { name: '…' }` scalar list the `!tagged_iterator`
     * / `service_collector` sites (the references-search word scan verifies candidates through
     * this method).
     */
    override fun isReferenceTo(element: PsiElement): Boolean {
        val declaration = ServicesYaml.tagNameDeclarationScalar(element)
        if (declaration != null) {
            return declaration.textValue.trim() == tagName
        }

        return super.isReferenceTo(element)
    }
}
