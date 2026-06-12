package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

/**
 * Self-reference carried by every tag-name position in `*.services.yml` — declarations under
 * `tags:` and consumers (`!tagged_iterator` / `!tagged_locator` arguments, `service_collector`
 * tags) alike.
 *
 * Resolving to itself is deliberate: it routes Ctrl+Click into the platform's Show Usages
 * branch instead of a choose-declaration popup (whose rows are bare target names — not usable
 * for "where is this tag"), and it makes every site a *usage* of every same-named site:
 * [isReferenceTo] accepts any tag position carrying the same name, so the references-search
 * word scan returns declarations and consumers together, rendered as native usages with file
 * names and code lines.
 */
class ServiceTagReference(
    element: PsiElement,
    private val tagName: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiReferenceBase<PsiElement>(element, rangeInElement) {

    override fun getCanonicalText(): String = tagName

    override fun resolve(): PsiElement = element

    override fun isReferenceTo(element: PsiElement): Boolean {
        val scalar = ServicesYaml.tagPositionScalar(element) ?: return false
        // The clicked site itself is not its own usage — without this, every usage list
        // (and the Show Usages popup) starts with the very element the search ran from.
        if (scalar == this.element) return false

        return ServicesYaml.tagNameAtPosition(scalar) == tagName
    }
}
