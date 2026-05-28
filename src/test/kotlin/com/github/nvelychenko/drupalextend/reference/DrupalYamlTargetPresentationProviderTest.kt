@file:Suppress("UnstableApiUsage")

package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalYamlTargetPresentationProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
    }

    fun testProvidesPresentationForDrupalYamlTargets() {
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function service(string $id): object {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "my_module.services.yml",
            """
            services:
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal::service('config.<caret>factory');
            """.trimIndent()
        )

        val target = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()
            ?: error("Expected service reference target")
        val presentation = DrupalYamlTargetPresentationProvider().getTargetPresentation(target, false)

        assertNotNull(presentation)
        assertEquals("my_module.services.yml", presentation?.presentableText)
        assertEquals("2", presentation?.containerText)
        assertTrue(presentation?.locationText?.contains("config.factory") == true)
    }
}
