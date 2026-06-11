package com.github.nvelychenko.drupalextend.util

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.drupal.DrupalVersion
import com.jetbrains.php.drupal.hooks.DrupalHooksIndex
import com.jetbrains.php.lang.psi.stubs.indexes.PhpFunctionNameIndex

/**
 * Shared hook-name suggestion source for the `#[Hook]` completion providers.
 *
 * Runs on every keystroke inside the attribute, so the index keys are streamed via
 * [FileBasedIndex.processAllKeys] (no materialized all-keys collection) and the loops are
 * cancellable. Only depends on classes from the optional `com.jetbrains.php.drupal` plugin,
 * like its callers.
 */
internal object DrupalHookSuggestions {

    fun matchingHookNames(prefixMatcher: PrefixMatcher, version: DrupalVersion, project: Project): Set<String> {
        val hookNames = hashSetOf<String>()
        collectFromDrupalHooksIndex(hookNames, prefixMatcher, version, project)
        collectFromApiDocFunctions(hookNames, prefixMatcher, project)

        return hookNames
    }

    private fun collectFromDrupalHooksIndex(
        hookNames: MutableSet<String>,
        prefixMatcher: PrefixMatcher,
        version: DrupalVersion,
        project: Project,
    ) {
        val index = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)
        index.processAllKeys(DrupalHooksIndex.KEY, { key ->
            ProgressManager.checkCanceled()
            if (key.suits(version)) {
                val hookName = key.name
                if (hookName !in hookNames && prefixMatcher.prefixMatches(hookName)) {
                    // Presence probe: stop at the first value instead of materializing the
                    // containing-file list for every candidate key.
                    val hasValueInScope =
                        !index.processValues(DrupalHooksIndex.KEY, key, null, { _, _ -> false }, searchScope)
                    if (hasValueInScope) {
                        hookNames.add(hookName)
                    }
                }
            }
            true
        }, project)
    }

    private fun collectFromApiDocFunctions(
        hookNames: MutableSet<String>,
        prefixMatcher: PrefixMatcher,
        project: Project,
    ) {
        FileBasedIndex.getInstance().processAllKeys(PhpFunctionNameIndex.KEY, { functionName ->
            ProgressManager.checkCanceled()
            if (functionName.startsWith("hook_")) {
                val hookName = functionName.substring("hook_".length)
                if (prefixMatcher.prefixMatches(hookName)) {
                    hookNames.add(hookName)
                }
            }
            true
        }, project)
    }
}
