package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.json.JsonFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalSchemaProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isRoutesCompletionEnabled = true
    }

    fun testRoutingProviderAvailableForRoutingYamlOnly() {
        val provider = DrupalRoutingSchemaProviderFactory().getProviders(project).single()

        val routingFile = myFixture.configureByText("my_module.routing.yml", "").virtualFile
        val unrelatedFile = myFixture.configureByText("my_module.services.yml", "").virtualFile

        assertTrue(provider.isAvailable(routingFile))
        assertFalse(provider.isAvailable(unrelatedFile))
    }

    fun testPermissionsProviderAvailableForPermissionsYamlOnly() {
        val provider = DrupalPermissionsSchemaProviderFactory().getProviders(project).single()

        val permissionsFile = myFixture.configureByText("my_module.permissions.yml", "").virtualFile
        val unrelatedFile = myFixture.configureByText("my_module.routing.yml", "").virtualFile

        assertTrue(provider.isAvailable(permissionsFile))
        assertFalse(provider.isAvailable(unrelatedFile))
    }

    fun testRoutingProviderUnavailableWhenSupportDisabled() {
        project.drupalExtendSettings.isRoutesCompletionEnabled = false
        val provider = DrupalRoutingSchemaProviderFactory().getProviders(project).single()

        val routingFile = myFixture.configureByText("my_module.routing.yml", "").virtualFile
        assertFalse(provider.isAvailable(routingFile))
    }

    fun testRoutingSchemaResourceLoadsAndParses() {
        assertSchemaResourceParses(
            DrupalRoutingSchemaProviderFactory().getProviders(project).single().schemaFile,
            "drupal-routing.schema.json"
        )
    }

    fun testPermissionsSchemaResourceLoadsAndParses() {
        assertSchemaResourceParses(
            DrupalPermissionsSchemaProviderFactory().getProviders(project).single().schemaFile,
            "drupal-permissions.schema.json"
        )
    }

    private fun assertSchemaResourceParses(schemaFile: VirtualFile?, expectedName: String) {
        assertNotNull("Schema resource $expectedName must resolve on the classpath", schemaFile)
        val text = String(schemaFile!!.contentsToByteArray(), Charsets.UTF_8)
        assertTrue(text.isNotBlank())

        // Parsing the bundled schema as JSON guards against malformed resources shipping in the plugin.
        val jsonPsi = PsiFileFactory.getInstance(project)
            .createFileFromText(expectedName, JsonFileType.INSTANCE, text)
        assertFalse(
            "Bundled schema $expectedName must parse as valid JSON",
            PsiTreeUtil.hasErrorElements(jsonPsi)
        )
    }
}
