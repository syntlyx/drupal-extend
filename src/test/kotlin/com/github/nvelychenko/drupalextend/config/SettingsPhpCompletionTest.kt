package com.github.nvelychenko.drupalextend.config

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SettingsPhpCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true

        myFixture.addFileToProject(
            "modules/my_module/config/install/my_module.settings.yml",
            """
            page:
              front: /node
              403: /403
            maintenance_mode: false
            """.trimIndent()
        )

        // A second config object so the name index has more than one candidate and completion
        // shows a list instead of auto-inserting a single match.
        myFixture.addFileToProject(
            "modules/my_module/config/install/my_module.other.yml",
            """
            enabled: true
            """.trimIndent()
        )
    }

    fun testConfigNameIndexSuggestsConfigObjectNames() {
        myFixture.configureByText(
            "settings.php",
            $$"""
            <?php

            $config['<caret>'];
            """.trimIndent()
        )

        val lookupStrings = (myFixture.complete(CompletionType.BASIC) ?: emptyArray()).map { it.lookupString }

        assertContainsElements(lookupStrings, "my_module.settings")
    }

    fun testConfigKeyIndexSuggestsConfigKeyPaths() {
        myFixture.configureByText(
            "settings.php",
            $$"""
            <?php

            $config['my_module.settings']['<caret>'];
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC).map { it.lookupString }

        assertContainsElements(lookupStrings, "page", "page.front", "page.403", "maintenance_mode")
    }

    fun testSettingsArrayKeySuggestsCoreSettingsKeys() {
        myFixture.configureByText(
            "settings.php",
            $$"""
            <?php

            $settings['<caret>'];
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC).map { it.lookupString }

        assertContainsElements(lookupStrings, "hash_salt", "trusted_host_patterns", "config_sync_directory")
    }

    fun testConfigNameIndexGetsNoSuggestionsOutsideSettingsPhp() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $config['my_module.<caret>'];
            """.trimIndent()
        )

        val lookupStrings = (myFixture.complete(CompletionType.BASIC) ?: emptyArray()).map { it.lookupString }

        assertFalse(lookupStrings.contains("my_module.settings"))
    }
}
