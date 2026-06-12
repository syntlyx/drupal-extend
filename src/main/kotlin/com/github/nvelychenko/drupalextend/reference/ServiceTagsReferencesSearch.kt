package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

/**
 * Feeds the references-search word scan for a tag declaration scalar: occurrences of the tag
 * name are checked against the references at each occurrence, and [ServiceTagReference]'s
 * `isReferenceTo` accepts same-named declarations — so `!tagged_iterator` / `service_collector`
 * consumer sites come back as usages. Mirrors [ServicesReferencesSearch].
 */
class ServiceTagsReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val elementToSearch = queryParameters.elementToSearch
        val project = elementToSearch.project
        if (!Services.isSupportEnabled(project)) return true

        val searchRequest = ReadAction.compute<Pair<String, SearchScope>?, RuntimeException> {
            val tagScalar = ServicesYaml.tagPositionScalar(elementToSearch) ?: return@compute null
            val name = ServicesYaml.tagNameAtPosition(tagScalar) ?: return@compute null
            name to queryParameters.effectiveSearchScope
        } ?: return true

        val tagName = searchRequest.first
        if (tagName.isBlank() || tagName.contains("%")) return true

        queryParameters.optimizer.searchWord(
            tagName,
            searchRequest.second,
            UsageSearchContext.ANY,
            true,
            elementToSearch
        )

        return true
    }
}
