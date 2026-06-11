package com.github.nvelychenko.drupalextend.config

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.ConfigObjectReference
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.yaml.psi.YAMLFile

class ConfigNameSupportTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    private fun addConfigFile() {
        myFixture.addFileToProject(
            "modules/custom/my_module/config/install/my_module.settings.yml",
            """
            langcode: en
            page:
              front: '/node'
              maintenance: '/maintenance'
            """.trimIndent()
        )
    }

    private fun addDrupalStub() {
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function config(string $name): object {}
            }
            """.trimIndent()
        )
    }

    fun testDrupalConfigCompletionSuggestsConfigName() {
        addConfigFile()
        addDrupalStub()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal::config('my_mo<caret>');
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                <?php

                \Drupal::config('my_module.settings');
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "my_module.settings")
    }

    fun testInstanceConfigMethodCompletionSuggestsConfigName() {
        addConfigFile()
        myFixture.configureByText(
            "FormController.php",
            $$"""
            <?php

            class FormController {
              public function config(string $name): object {}

              public function build() {
                $this->config('my_mo<caret>');
              }
            }
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            assertTrue(myFixture.file.text.contains("\$this->config('my_module.settings')"))
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "my_module.settings")
    }

    fun testConfigFactoryGetEditableCompletionSuggestsConfigName() {
        addConfigFile()
        myFixture.configureByText(
            "FormController.php",
            $$"""
            <?php

            class FormController {
              protected function configFactory(): object {}

              public function build() {
                $this->configFactory()->getEditable('my_mo<caret>');
              }
            }
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            assertTrue(myFixture.file.text.contains("getEditable('my_module.settings')"))
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "my_module.settings")
    }

    fun testDrupalConfigNameLiteralResolvesToYamlFile() {
        addConfigFile()
        addDrupalStub()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal::config('my_module.settings');
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildrenOfType(myFixture.file, StringLiteralExpression::class.java)
            .single { it.contents == "my_module.settings" }
        val reference = literal.references.filterIsInstance<ConfigObjectReference>().single()
        val resolved = reference.resolve()

        assertTrue(resolved is YAMLFile)
        assertEquals("my_module.settings.yml", (resolved as YAMLFile).name)
    }

    fun testUnrelatedCallGetsNoConfigObjectReference() {
        addConfigFile()
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            class FormController {
              public function build() {
                $this->doSomething('my_module.settings');
              }
            }
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildrenOfType(myFixture.file, StringLiteralExpression::class.java)
            .single { it.contents == "my_module.settings" }

        assertFalse(literal.references.any { it is ConfigObjectReference })
    }

    fun testSettingsPhpConfigOverrideNameGetsReference() {
        addConfigFile()
        myFixture.configureByText(
            "settings.php",
            $$"""
            <?php

            $config['my_module.settings']['page']['front'] = '/home';
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildrenOfType(myFixture.file, StringLiteralExpression::class.java)
            .single { it.contents == "my_module.settings" }
        val reference = literal.references.filterIsInstance<ConfigObjectReference>().single()
        val resolved = reference.resolve()

        assertTrue(resolved is YAMLFile)
        assertEquals("my_module.settings.yml", (resolved as YAMLFile).name)
    }
}
