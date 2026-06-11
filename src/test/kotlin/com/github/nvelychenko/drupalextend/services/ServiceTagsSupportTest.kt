package com.github.nvelychenko.drupalextend.services

import com.github.nvelychenko.drupalextend.inspection.ServicesYamlInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceTagReference
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLKeyValue

class ServiceTagsSupportTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
    }

    private fun addTaggedServices() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              my_module.breadcrumb:
                class: Drupal\my_module\Breadcrumb
                tags:
                  - { name: breadcrumb_builder, priority: 100 }
              my_module.thing_one:
                class: Drupal\my_module\ThingOne
                tags:
                  - { name: my_module.collector }
              my_module.thing_two:
                class: Drupal\my_module\ThingTwo
                tags:
                  - { name: my_module.collector }
            """.trimIndent()
        )
    }

    fun testTagNameCompletionUnderTagsSuggestsProjectAndCuratedCoreTags() {
        addTaggedServices()
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                class: Drupal\consumer\Consumer
                tags:
                  - { name: <caret> }
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertContainsElements(lookupStrings, "my_module.collector", "breadcrumb_builder", "event_subscriber")
    }

    fun testTaggedIteratorScalarFormCompletion() {
        addTaggedServices()
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                class: Drupal\consumer\Consumer
                arguments:
                  - !tagged_iterator <caret>
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertContainsElements(lookupStrings, "my_module.collector", "breadcrumb_builder")
    }

    fun testTaggedIteratorMappingFormCompletion() {
        addTaggedServices()
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                class: Drupal\consumer\Consumer
                arguments:
                  - !tagged_iterator { tag: <caret> }
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertContainsElements(lookupStrings, "my_module.collector", "breadcrumb_builder")
    }

    fun testTaggedIteratorReferenceResolvesToTaggedServices() {
        addTaggedServices()
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                class: Drupal\consumer\Consumer
                arguments:
                  - !tagged_iterator my_module.collec<caret>tor
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertTrue("expected a ServiceTagReference", reference is ServiceTagReference)

        val resolvedServiceIds = (reference as ServiceTagReference).multiResolve(false)
            .mapNotNull {
                PsiTreeUtil.getParentOfType(it.element, YAMLKeyValue::class.java, false)?.keyText
            }

        assertContainsElements(resolvedServiceIds, "my_module.thing_one", "my_module.thing_two")
    }

    fun testInspectionFlagsUnknownTagButSilentForKnownAndCuratedTags() {
        addTaggedServices()
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                class: Drupal\consumer\Consumer
                arguments:
                  - !tagged_iterator made.up.tag
                  - !tagged_iterator my_module.collector
                  - !tagged_iterator event_subscriber
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Unknown service tag 'made.up.tag'")
        assertFalse(descriptions.contains("Unknown service tag 'my_module.collector'"))
        assertFalse(descriptions.contains("Unknown service tag 'event_subscriber'"))
    }
}
