package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

class RoutesReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val elementToSearch = queryParameters.elementToSearch
        val project = elementToSearch.project
        if (!Routes.isSupportEnabled(project)) return true

        val searchRequest = ReadAction.compute<Pair<String, SearchScope>?, RuntimeException> {
            val definition = RoutingYaml.routeDefinitionFromKeyElement(elementToSearch)
                ?: RoutingYaml.linkDefinitionFromKeyElement(elementToSearch)
                ?: Permissions.permissionDefinitionFromKeyElement(elementToSearch)
                ?: return@compute null

            definition.keyText to queryParameters.effectiveSearchScope
        } ?: return true

        val name = searchRequest.first
        if (name.isBlank()) return true

        queryParameters.optimizer.searchWord(
            name,
            searchRequest.second,
            UsageSearchContext.ANY,
            true,
            elementToSearch
        )

        return true
    }
}
