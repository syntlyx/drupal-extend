package com.github.nvelychenko.drupalextend.php

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.type.ConfigObjectTypeProvider
import com.github.nvelychenko.drupalextend.type.EntityStorageTypeProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Our PhpTypeProvider4 implementations attach marker types to expressions. The GLOBAL type of
 * an expression is what PhpStorm's built-in inspections (e.g. "Expected parameter of type 'X',
 * 'X' provided") consume — markers must never leak into it unresolved, or same-type arguments
 * start looking mismatched. These tests pin the global-type purity of our chains.
 */
class BuiltinParamsInteractionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
    }

    fun testServiceTypedVariableGlobalTypeResolvesToClassWithoutMarkers() {
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
            "modules/custom/nll_core/src/Application/Application.php",
            $$"""
            <?php

            namespace Drupal\nll_core\Application;

            class Application {
              public function id(): string { return ''; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/nll_core/nll_core.services.yml",
            """
            services:
              nll_core.application:
                class: Drupal\nll_core\Application\Application
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            namespace Drupal\nll_core;

            class Consumer {
              public function run(): void {
                $application = \Drupal::service('nll_core.application');
                $application->id();
              }
            }
            """.trimIndent()
        )

        val variable = PsiTreeUtil.findChildrenOfType(file, Variable::class.java)
            .first { it.name == "application" }
        val globalTypes = (variable as PhpTypedElement).type.global(project).types

        assertTrue(
            "Global type must resolve to the service class, got: $globalTypes",
            globalTypes.any { it == "\\Drupal\\nll_core\\Application\\Application" }
        )
        assertNoMarkersLeak(globalTypes)
    }

    fun testConfigTypedVariableGlobalTypeHasNoMarkers() {
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function config(string $name): \Drupal\Core\Config\ImmutableConfig {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Config/ImmutableConfig.php",
            $$"""
            <?php

            namespace Drupal\Core\Config;

            class ImmutableConfig {
              public function get(string $key = ''): mixed {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/config/install/my_module.settings.yml",
            """
            ui:
              mode: compact
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            namespace Drupal\my_module;

            class Consumer {
              public function run(): void {
                $config = \Drupal::config('my_module.settings');
                $config->get('ui.mode');
              }
            }
            """.trimIndent()
        )

        val variable = PsiTreeUtil.findChildrenOfType(file, Variable::class.java)
            .first { it.name == "config" }
        val globalTypes = (variable as PhpTypedElement).type.global(project).types

        assertTrue(
            "Global type must keep the native ImmutableConfig, got: $globalTypes",
            globalTypes.any { it == "\\Drupal\\Core\\Config\\ImmutableConfig" }
        )
        assertNoMarkersLeak(globalTypes)
    }

    private fun assertNoMarkersLeak(globalTypes: Set<String>) {
        val markerKeys = charArrayOf(
            ConfigObjectTypeProvider.KEY,
            ConfigObjectTypeProvider.SPLIT_KEY,
            EntityStorageTypeProvider.Util.KEY,
            EntityStorageTypeProvider.Util.SPLIT_KEY,
        )
        val leaked = globalTypes.filter { type -> markerKeys.any { it in type } }

        assertEmpty("Marker types leaked into the global type: $leaked", leaked)
    }
}
