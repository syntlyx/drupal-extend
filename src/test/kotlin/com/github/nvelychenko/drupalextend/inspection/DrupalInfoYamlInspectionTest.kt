package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalInfoYamlInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        myFixture.enableInspections(DrupalInfoYamlInspection())
    }

    private fun highlightDescriptions(fileName: String, content: String): List<String> {
        myFixture.configureByText(fileName, content.trimIndent())
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    fun testValidModuleInfoYamlIsSilent() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            core_version_requirement: ^11
            dependencies:
              - pathauto:pathauto
            """
        )

        assertFalse(descriptions.any { it.contains("info.yml") })
        assertFalse(descriptions.any { it.startsWith("Invalid extension type") })
        assertFalse(descriptions.any { it.startsWith("Missing 'core_version_requirement'") })
        assertFalse(descriptions.any { it.startsWith("Invalid dependency") })
    }

    fun testMissingTypeIsReported() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            core_version_requirement: ^11
            """
        )

        assertContainsElements(descriptions, "Missing required key 'type' in info.yml")
    }

    fun testInvalidTypeValueIsReported() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            type: widget
            core_version_requirement: ^11
            """
        )

        assertTrue(descriptions.any { it.startsWith("Invalid extension type 'widget'") })
    }

    fun testMissingCoreVersionRequirementWithoutLegacyCoreIsReported() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            """
        )

        assertContainsElements(descriptions, "Missing 'core_version_requirement'")
    }

    fun testLegacyCoreKeySatisfiesCoreVersionRequirement() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            core: 8.x
            """
        )

        assertFalse(descriptions.contains("Missing 'core_version_requirement'"))
    }

    fun testBadDependencyEntryIsWeakWarning() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            core_version_requirement: ^11
            dependencies:
              - Not A Module
            """
        )

        assertTrue(descriptions.any { it.startsWith("Invalid dependency 'Not A Module'") })
    }

    fun testProjectScopedDependencyIsSilent() {
        val descriptions = highlightDescriptions(
            "my_module.info.yml",
            """
            name: My Module
            type: module
            core_version_requirement: ^11
            dependencies:
              - pathauto:pathauto
            """
        )

        assertFalse(descriptions.any { it.startsWith("Invalid dependency") })
    }

    fun testNonInfoYamlFileIsSilent() {
        val descriptions = highlightDescriptions(
            "my_module.routing.yml",
            """
            some_route:
              path: /foo
            """
        )

        assertFalse(descriptions.any { it.contains("info.yml") })
        assertFalse(descriptions.contains("Missing 'core_version_requirement'"))
    }
}
