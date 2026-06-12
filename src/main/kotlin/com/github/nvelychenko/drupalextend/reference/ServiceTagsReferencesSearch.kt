package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Feeds the references-search word scan for a tag-name scalar (declaration under `tags:` or a
 * `!tagged_iterator` / `service_collector` consumer). Tag positions deliberately carry no
 * [PsiReference] of their own — one would hijack Ctrl+Click away from the Show Usages branch
 * (see [ServiceTagPomDeclarationSearcher]) — so a custom [RequestResultProcessor] manufactures
 * a usage reference per matching occurrence.
 *
 * The two sides are dual, not symmetric: usages of a *declaration* are the consumer sites
 * (usually exactly one, so Show Usages jumps straight there), and usages of a *consumer* are
 * the declarations carrying the tag. Same-kind siblings — other services declaring the same
 * tag, or other consumers — are not usages of each other.
 */
class ServiceTagsReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val elementToSearch = queryParameters.elementToSearch
        val project = elementToSearch.project
        if (!Services.isSupportEnabled(project)) return true

        val searchRequest = ReadAction.compute<Triple<YAMLScalar, String, SearchScope>?, RuntimeException> {
            val tagScalar = ServicesYaml.tagPositionScalar(ServiceTagPomTarget.unwrap(elementToSearch))
                ?: return@compute null
            val name = ServicesYaml.tagNameAtPosition(tagScalar) ?: return@compute null
            Triple(tagScalar, name, queryParameters.effectiveSearchScope)
        } ?: return true

        val (tagScalar, tagName, scope) = searchRequest
        if (tagName.isBlank() || tagName.contains("%")) return true

        queryParameters.optimizer.searchWord(
            tagName,
            scope,
            UsageSearchContext.ANY,
            true,
            tagScalar,
            ServiceTagOccurrenceProcessor(tagScalar, tagName, ServicesYaml.isTagNameValuePosition(tagScalar))
        )

        return true
    }

    /**
     * Turns a word-scan occurrence into a usage when the occurrence is a same-named tag position
     * of the *opposite* kind: consumers are usages of a declaration, declarations are usages of
     * a consumer. Same-kind siblings (which includes the searched site itself) are skipped.
     */
    private class ServiceTagOccurrenceProcessor(
        private val target: PsiElement,
        private val tagName: String,
        private val targetIsDeclaration: Boolean,
    ) : RequestResultProcessor(target, tagName, targetIsDeclaration) {
        override fun processTextOccurrence(
            element: PsiElement,
            offsetInElement: Int,
            consumer: Processor<in PsiReference>
        ): Boolean {
            // The word-scan walks the tree up from the leaf; react only on the scalar itself so
            // each occurrence produces exactly one usage.
            val scalar = element as? YAMLScalar ?: return true
            if (ServicesYaml.tagPositionScalar(scalar) != scalar) return true
            if (ServicesYaml.tagNameAtPosition(scalar) != tagName) return true
            if (ServicesYaml.isTagNameValuePosition(scalar) == targetIsDeclaration) return true

            return consumer.process(ServiceTagUsageReference(scalar, tagName, target))
        }
    }

    private class ServiceTagUsageReference(
        scalar: YAMLScalar,
        tagName: String,
        private val target: PsiElement,
    ) : PsiReferenceBase<YAMLScalar>(scalar, ServicesYaml.scalarTextValueRange(scalar, tagName)) {
        override fun resolve(): PsiElement = target
    }
}
