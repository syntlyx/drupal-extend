package com.github.nvelychenko.drupalextend.config

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConfigKeyCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true

        myFixture.addFileToProject(
            "modules/my_module/config/install/my_module.settings.yml",
            """
            ui:
              mode: compact
              theme: dark
            limit: 10
            """.trimIndent()
        )

        // Minimal \Drupal::config() stub returning an ImmutableConfig with get(string $key).
        myFixture.addFileToProject(
            "stubs/drupal_config.php",
            """
            <?php

            namespace Drupal\Core\Config {
              class ImmutableConfig {
                public function get(${'$'}key) {}
              }
            }

            namespace {
              class Drupal {
                public static function config(${'$'}name): \Drupal\Core\Config\ImmutableConfig {}
              }
            }
            """.trimIndent()
        )
    }

    fun testDirectChainSuggestsAllConfigKeyPaths() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            ${'$'}value = \Drupal::config('my_module.settings')->get('<caret>');
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC).map { it.lookupString }

        assertContainsElements(lookupStrings, "ui", "ui.mode", "ui.theme", "limit")
    }

    fun testDirectChainPrefixFiltersConfigKeyPaths() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            ${'$'}value = \Drupal::config('my_module.settings')->get('u<caret>');
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC).map { it.lookupString }

        assertContainsElements(lookupStrings, "ui", "ui.mode", "ui.theme")
        assertFalse(lookupStrings.contains("limit"))
    }

    fun testThroughVariableSuggestsConfigKeyPaths() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            ${'$'}config = \Drupal::config('my_module.settings');
            ${'$'}value = ${'$'}config->get('<caret>');
            """.trimIndent()
        )

        val lookupStrings = (myFixture.complete(CompletionType.BASIC) ?: emptyArray()).map { it.lookupString }

        // The variable path exercises ConfigObjectTypeProvider. If the headless fixture's
        // type resolution proves flaky, the direct-chain test above still guards the feature.
        assertContainsElements(lookupStrings, "ui", "ui.mode", "ui.theme", "limit")
    }

    fun testNonConfigReceiverGetsNoConfigKeys() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            ${'$'}value = ${'$'}something->get('<caret>');
            """.trimIndent()
        )

        val lookupStrings = (myFixture.complete(CompletionType.BASIC) ?: emptyArray()).map { it.lookupString }

        assertFalse(lookupStrings.contains("ui"))
        assertFalse(lookupStrings.contains("ui.mode"))
        assertFalse(lookupStrings.contains("limit"))
    }
}
