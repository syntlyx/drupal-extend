package com.github.nvelychenko.drupalextend.completion

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CacheContextsCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true

        myFixture.addFileToProject(
            "core/core.services.yml",
            """
            services:
              cache_context.user:
                class: Drupal\Core\Cache\Context\UserCacheContext
              cache_context.url.path:
                class: Drupal\Core\Cache\Context\PathCacheContext
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
            """.trimIndent()
        )
    }

    fun testAddCacheContextsMethodArraySuggestsContextIdsOnly() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $renderer->addCacheContexts(['<caret>']);
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC).map { it.lookupString }

        assertContainsElements(lookupStrings, "user", "url.path")
        assertFalse(lookupStrings.contains("config.factory"))
        assertFalse(lookupStrings.contains("cache_context.user"))
    }

    fun testRenderCacheContextsArraySuggestsContextIds() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $build = [
              '#cache' => [
                'contexts' => ['<caret>'],
              ],
            ];
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC).map { it.lookupString }

        assertContainsElements(lookupStrings, "user", "url.path")
        assertFalse(lookupStrings.contains("config.factory"))
    }

    fun testUnrelatedMethodArrayGetsNoCacheContextSuggestions() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $renderer->setAttributes(['<caret>']);
            """.trimIndent()
        )

        val lookupStrings = (myFixture.complete(CompletionType.BASIC) ?: emptyArray()).map { it.lookupString }

        assertFalse(lookupStrings.contains("user"))
        assertFalse(lookupStrings.contains("url.path"))
    }

    fun testUnrelatedRenderArrayKeyGetsNoCacheContextSuggestions() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $build = [
              '#cache' => [
                'tags' => ['<caret>'],
              ],
            ];
            """.trimIndent()
        )

        val lookupStrings = (myFixture.complete(CompletionType.BASIC) ?: emptyArray()).map { it.lookupString }

        assertFalse(lookupStrings.contains("user"))
        assertFalse(lookupStrings.contains("url.path"))
    }
}
