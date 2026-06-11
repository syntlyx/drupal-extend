package com.github.nvelychenko.drupalextend.config

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.ModuleReference
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar

class ModuleNameSupportTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        addExtension("node", "module")
        addExtension("user", "module")
        addExtension("olivero", "theme")
    }

    private fun addExtension(machineName: String, type: String) {
        myFixture.addFileToProject(
            "modules/contrib/$machineName/$machineName.info.yml",
            """
            name: ${machineName.replaceFirstChar { it.uppercase() }}
            type: $type
            core_version_requirement: ^11
            """.trimIndent()
        )
    }

    private fun literalWithContents(contents: String): StringLiteralExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, StringLiteralExpression::class.java)
            .single { it.contents == contents }

    fun testModuleExistsCompletionSuggestsMachineNames() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            class Consumer {
              protected function moduleHandler(): object {}

              public function build() {
                $this->moduleHandler()->moduleExists('no<caret>');
              }
            }
            """.trimIndent()
        )

        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups == null) {
            assertTrue(myFixture.file.text.contains("moduleExists('node')"))
            return
        }

        assertContainsElements(lookups.map { it.lookupString }, "node", "user")
    }

    fun testModuleExistsLiteralResolvesToInfoYaml() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            class Consumer {
              protected function moduleHandler(): object {}

              public function build() {
                $this->moduleHandler()->moduleExists('node');
              }
            }
            """.trimIndent()
        )

        val reference = literalWithContents("node").references.filterIsInstance<ModuleReference>().single()
        val resolved = reference.resolve()

        assertTrue(resolved is YAMLFile)
        assertEquals("node.info.yml", (resolved as YAMLFile).name)
    }

    fun testModulesFieldCompletionFiltersOutThemes() {
        myFixture.configureByText(
            "MyTest.php",
            $$"""
            <?php

            class MyTest {
              protected static $modules = ['no<caret>'];
            }
            """.trimIndent()
        )

        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups == null) {
            assertTrue(myFixture.file.text.contains("['node']"))
            return
        }

        val strings = lookups.map { it.lookupString }
        assertContainsElements(strings, "node", "user")
        assertFalse(strings.contains("olivero"))
    }

    fun testModulesFieldLiteralResolvesToInfoYaml() {
        myFixture.configureByText(
            "MyTest.php",
            $$"""
            <?php

            class MyTest {
              protected static $modules = ['user'];
            }
            """.trimIndent()
        )

        val reference = literalWithContents("user").references.filterIsInstance<ModuleReference>().single()
        val resolved = reference.resolve()

        assertTrue(resolved is YAMLFile)
        assertEquals("user.info.yml", (resolved as YAMLFile).name)
    }

    fun testInfoYamlDependencyResolvesToInfoYaml() {
        myFixture.configureByText(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            core_version_requirement: ^11
            dependencies:
              - node
            """.trimIndent()
        )

        val scalar = PsiTreeUtil.findChildrenOfType(myFixture.file, YAMLScalar::class.java)
            .single { it.textValue == "node" }
        val reference = scalar.references.filterIsInstance<ModuleReference>().single()
        val resolved = reference.resolve()

        assertTrue(resolved is YAMLFile)
        assertEquals("node.info.yml", (resolved as YAMLFile).name)
    }

    fun testInfoYamlProjectScopedDependencyResolvesModulePart() {
        myFixture.configureByText(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            core_version_requirement: ^11
            dependencies:
              - drupal:user
            """.trimIndent()
        )

        val scalar = PsiTreeUtil.findChildrenOfType(myFixture.file, YAMLScalar::class.java)
            .single { it.textValue == "drupal:user" }
        val reference = scalar.references.filterIsInstance<ModuleReference>().single()

        assertEquals("user", reference.canonicalText)
        val resolved = reference.resolve()
        assertTrue(resolved is YAMLFile)
        assertEquals("user.info.yml", (resolved as YAMLFile).name)
    }

    fun testArbitraryStringGetsNoModuleReference() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            class Consumer {
              public function build() {
                $this->doSomething('node');
              }
            }
            """.trimIndent()
        )

        assertFalse(literalWithContents("node").references.any { it is ModuleReference })
    }
}
