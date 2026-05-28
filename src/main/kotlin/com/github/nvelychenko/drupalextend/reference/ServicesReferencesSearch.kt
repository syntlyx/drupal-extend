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

class ServicesReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val elementToSearch = queryParameters.elementToSearch
        val project = elementToSearch.project
        if (!Services.isSupportEnabled(project)) return true

        val searchRequest = ReadAction.compute<Pair<String, SearchScope>?, RuntimeException> {
            val serviceDefinition = ServicesYaml.serviceDefinitionFromKeyElement(elementToSearch) ?: return@compute null
            serviceDefinition.keyText to queryParameters.effectiveSearchScope
        } ?: return true

        val serviceId = searchRequest.first
        if (serviceId.isBlank()) return true

        queryParameters.optimizer.searchWord(
            serviceId,
            searchRequest.second,
            UsageSearchContext.ANY,
            true,
            elementToSearch
        )

        return true
    }
}
