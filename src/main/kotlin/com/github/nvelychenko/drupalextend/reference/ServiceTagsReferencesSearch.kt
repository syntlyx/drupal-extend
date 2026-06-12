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
 * (see [ServiceTagSymbolDeclarationProvider]) — so a custom [RequestResultProcessor]
 * manufactures a usage reference for every same-named tag position the word scan hits:
 * declarations and consumers come back as usages of each other.
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
            ServiceTagOccurrenceProcessor(tagScalar, tagName)
        )

        return true
    }

    /**
     * Turns a word-scan occurrence into a usage when the occurrence element is itself a tag-name
     * scalar carrying the searched name. The searched site is skipped — the usage list must not
     * start with the very element the search ran from.
     */
    private class ServiceTagOccurrenceProcessor(
        private val target: PsiElement,
        private val tagName: String,
    ) : RequestResultProcessor(target, tagName) {
        override fun processTextOccurrence(
            element: PsiElement,
            offsetInElement: Int,
            consumer: Processor<in PsiReference>
        ): Boolean {
            // The word-scan walks the tree up from the leaf; react only on the scalar itself so
            // each occurrence produces exactly one usage.
            val scalar = element as? YAMLScalar ?: return true
            if (scalar == target) return true
            if (ServicesYaml.tagPositionScalar(scalar) != scalar) return true
            if (ServicesYaml.tagNameAtPosition(scalar) != tagName) return true

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
