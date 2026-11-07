package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.project.DrupalConfigDirectories
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.DrupalConfigUsagePlugins
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Find Usages on a config-enabled plugin class searches for the class *name* by default, which
 * never occurs in config YAML — the `plugin_id: <id>` lines that actually make the plugin
 * reachable would stay invisible. This executor feeds the word scan with the plugin *id*, so
 * the [com.github.nvelychenko.drupalextend.reference.referenceType.DrupalPluginIdReference]
 * occurrences in config surface as regular usages.
 */
class PluginClassReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ): Boolean {
        val searchRequest = ReadAction.compute<Triple<PhpClass, String, SearchScope>?, RuntimeException> {
            val phpClass = queryParameters.elementToSearch as? PhpClass ?: return@compute null
            if (!phpClass.project.drupalExtendSettings.isEnabled) return@compute null
            val (_, pluginId) = DrupalConfigUsagePlugins.pluginKindAndId(phpClass) ?: return@compute null
            Triple(phpClass, pluginId, queryParameters.effectiveSearchScope)
        } ?: return true

        val (phpClass, pluginId, scope) = searchRequest
        if (pluginId.isBlank()) return true

        // The default Find Usages scope ("Project Files") doesn't cover excluded config
        // directories that are only indexed through the synthetic config library — widen the
        // word scan by exactly those directories so the enabling config always surfaces.
        val configScope = ReadAction.compute<SearchScope?, RuntimeException> {
            DrupalConfigDirectories.searchScope(phpClass.project)
        }
        val effectiveScope = configScope?.let { scope.union(it) } ?: scope

        queryParameters.optimizer.searchWord(pluginId, effectiveScope, UsageSearchContext.ANY, true, phpClass)

        return true
    }
}
