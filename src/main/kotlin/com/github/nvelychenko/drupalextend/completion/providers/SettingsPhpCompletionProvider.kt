package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Configs
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Completion inside `settings*.php` for the two Drupal globals:
 * - `$config['name']` / `$config['name']['key.path']` — config-object overrides, fed by the
 *   config-object index.
 * - `$settings['…']` — core Settings keys, from a curated static list.
 *
 * Registered on a broad `StringLiteralExpression` pattern, so this provider must be cheap to
 * reject: every entry point goes through a `Configs` helper that checks the containing file name
 * first, bailing out O(1) for ordinary PHP files before any index access.
 */
class SettingsPhpCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project

        if (!Configs.isSupportEnabled(project)) return

        val literal = PsiTreeUtil.getParentOfType(leaf, StringLiteralExpression::class.java, false) ?: return

        // `$config['…']['key…']` — second index is a config key path. Checked before the name
        // index because the name helper would also match a nested access' inner literal.
        val configNameForKey = Configs.settingsConfigOverrideNameForKey(literal)
        if (configNameForKey != null) {
            if (DumbService.isDumb(project)) return

            val keyResultSet = completionResultSet.withPrefixMatcher(literal.contents)
            Configs.keyPaths(project, configNameForKey)
                .forEach { keyResultSet.addElement(LookupElementBuilder.create(it)) }

            completionResultSet.stopHere()
            return
        }

        // `$config['…']` — first index is a config object name.
        if (Configs.isSettingsConfigOverrideName(literal)) {
            if (DumbService.isDumb(project)) return

            val nameResultSet = completionResultSet.withPrefixMatcher(literal.contents)
            Configs.allNames(project)
                .forEach { nameResultSet.addElement(LookupElementBuilder.create(it)) }

            completionResultSet.stopHere()
            return
        }

        // `$settings['…']` — core Settings keys from the curated list.
        if (Configs.isSettingsArrayKey(literal)) {
            val settingsResultSet = completionResultSet.withPrefixMatcher(literal.contents)
            CORE_SETTINGS_KEYS
                .forEach { settingsResultSet.addElement(LookupElementBuilder.create(it)) }

            completionResultSet.stopHere()
            return
        }
    }

    private companion object {
        /**
         * Curated subset of `$settings` keys recognised by Drupal core's
         * `sites/default/default.settings.php` (Drupal 11.3). Intentionally not exhaustive —
         * these are the common, stable keys worth completing.
         */
        private val CORE_SETTINGS_KEYS = listOf(
            "hash_salt",
            "config_sync_directory",
            "trusted_host_patterns",
            "file_public_path",
            "file_private_path",
            "file_temp_path",
            "file_assets_path",
            "update_free_access",
            "rebuild_access",
            "reverse_proxy",
            "reverse_proxy_addresses",
            "reverse_proxy_trusted_headers",
            "container_yamls",
            "bootstrap_container_definition",
            "deployment_identifier",
            "maintenance_theme",
            "entity_update_batch_size",
            "entity_update_backup",
            "state_cache",
            "cache_prefix",
            "class_loader_auto_detect",
            "allow_authorize_operations",
            "skip_permissions_hardening",
            "config_exclude_modules",
            "extension_discovery_scan_tests",
            "file_chmod_directory",
            "file_chmod_file",
            "php_storage",
            "queue_default",
        )
    }
}
