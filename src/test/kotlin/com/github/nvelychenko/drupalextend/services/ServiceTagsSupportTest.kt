package com.github.nvelychenko.drupalextend.services

import com.github.nvelychenko.drupalextend.inspection.ServicesYamlInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceTagDeclarationReference
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

    fun testTagDeclarationNavigatesToConsumerOnly() {
        addTaggedServices()
        myFixture.addFileToProject(
            "modules/custom/my_module/consumer/consumer.services.yml",
            """
            services:
              my_module.registry:
                class: Drupal\my_module\Registry
                arguments:
                  - !tagged_iterator my_module.collector
            """.trimIndent()
        )
        myFixture.configureByText(
            "provider.services.yml",
            """
            services:
              provider.extra:
                class: Drupal\provider\Extra
                tags:
                  - { name: my_module.col<caret>lector }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertTrue("expected a ServiceTagDeclarationReference", reference is ServiceTagDeclarationReference)

        // Targets are the consumer services' definition keys — real PSI the popup presents
        // natively, the same shape the consumer-side reference resolves to.
        val resolvedServiceIds = (reference as ServiceTagDeclarationReference).multiResolve(false)
            .mapNotNull { it.element }
            .mapNotNull { PsiTreeUtil.getParentOfType(it, YAMLKeyValue::class.java, false)?.keyText }

        assertContainsElements(resolvedServiceIds, "my_module.registry")

        // The tag-carrying services are NOT targets here — listing them is the consumer-side
        // reference's job.
        assertFalse(resolvedServiceIds.contains("my_module.thing_one"))
        assertFalse(resolvedServiceIds.contains("my_module.thing_two"))
    }

    fun testTagDeclarationNavigatesToServiceCollectorConsumer() {
        addTaggedServices()
        myFixture.addFileToProject(
            "modules/custom/my_module/collector/collector.services.yml",
            """
            services:
              my_module.manager:
                class: Drupal\my_module\Manager
                tags:
                  - { name: service_collector, tag: my_module.collector, call: addHandler }
            """.trimIndent()
        )
        myFixture.configureByText(
            "provider.services.yml",
            """
            services:
              provider.extra:
                class: Drupal\provider\Extra
                tags:
                  - { name: my_module.col<caret>lector }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedServiceIds = (reference as ServiceTagDeclarationReference).multiResolve(false)
            .mapNotNull { it.element }
            .mapNotNull { PsiTreeUtil.getParentOfType(it, YAMLKeyValue::class.java, false)?.keyText }

        assertContainsElements(resolvedServiceIds, "my_module.manager")
    }

    fun testServiceCollectorTagValueNavigatesToTaggedServices() {
        addTaggedServices()
        myFixture.configureByText(
            "collector.services.yml",
            """
            services:
              my_module.manager:
                class: Drupal\my_module\Manager
                tags:
                  - { name: service_collector, tag: my_module.col<caret>lector }
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
