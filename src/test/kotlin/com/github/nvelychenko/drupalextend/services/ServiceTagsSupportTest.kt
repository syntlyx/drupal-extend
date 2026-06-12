package com.github.nvelychenko.drupalextend.services

import com.github.nvelychenko.drupalextend.inspection.ServicesYamlInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceTagReference
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.psi.search.searches.ReferencesSearch
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

    fun testTaggedIteratorConsumerUsagesListTagDeclarations() {
        // Clicking the tag name on the consumer side goes through the same usages flow: the
        // declarations carrying the tag come back as usages of the consumer position.
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
        assertEquals(
            "Tag reference must resolve to itself (usage-flow routing)",
            reference.element,
            reference.resolve()
        )

        val consumerScalar = ServicesYaml.tagPositionScalar(
            myFixture.file.findElementAt(myFixture.caretOffset)!!
        )!!
        val usageServiceIds = ReferencesSearch.search(consumerScalar).findAll()
            .mapNotNull { PsiTreeUtil.getParentOfType(it.element, YAMLKeyValue::class.java, false) }
            .mapNotNull { keyValue ->
                generateSequence(keyValue) { PsiTreeUtil.getParentOfType(it, YAMLKeyValue::class.java, true) }
                    .firstOrNull { ServicesYaml.isServiceDefinition(it) }?.keyText
            }

        assertContainsElements(usageServiceIds, "my_module.thing_one", "my_module.thing_two")
    }

    fun testTagDeclarationUsagesListTaggedIteratorConsumer() {
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

        val declaration = ServicesYaml.tagPositionScalar(
            myFixture.file.findElementAt(myFixture.caretOffset)!!
        )
        assertNotNull("Caret element must be recognized as a tag declaration", declaration)

        // Find Usages on the declaration lists the !tagged_iterator consumer site.
        val usages = ReferencesSearch.search(declaration!!).findAll()
        assertTrue(
            "Usages must include the !tagged_iterator consumer, got: " +
                usages.map { it.element.containingFile.name + ": " + it.element.text },
            usages.any {
                it.element.containingFile.name == "consumer.services.yml"
                    && it.element.text.contains("my_module.collector")
            }
        )
    }

    fun testTagReferenceResolvesToItselfAndExcludesSelfFromUsages() {
        // The self-resolve routes Ctrl+Click into the Show Usages branch; the clicked site
        // itself must not come back as its own usage.
        addTaggedServices()
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
        assertTrue("expected a ServiceTagReference", reference is ServiceTagReference)
        assertEquals(reference.element, reference.resolve())

        val scalar = ServicesYaml.tagPositionScalar(
            myFixture.file.findElementAt(myFixture.caretOffset)!!
        )!!
        val usageElements = ReferencesSearch.search(scalar).findAll().map { it.element }
        assertFalse(
            "The clicked declaration must not list itself as a usage",
            usageElements.any { it == scalar }
        )
        assertTrue(
            "Sibling declarations of the same tag must be usages",
            usageElements.any { it.containingFile.name == "my_module.services.yml" }
        )
    }

    fun testTagDeclarationUsagesListServiceCollectorConsumer() {
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

        val declaration = ServicesYaml.tagPositionScalar(
            myFixture.file.findElementAt(myFixture.caretOffset)!!
        )!!

        val usages = ReferencesSearch.search(declaration).findAll()
        assertTrue(
            "Usages must include the service_collector consumer, got: " +
                usages.map { it.element.containingFile.name + ": " + it.element.text },
            usages.any { it.element.containingFile.name == "collector.services.yml" }
        )
    }

    fun testServiceCollectorTagValueUsagesListTagDeclarations() {
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

        val collectorScalar = ServicesYaml.tagPositionScalar(
            myFixture.file.findElementAt(myFixture.caretOffset)!!
        )!!
        val usageFiles = ReferencesSearch.search(collectorScalar).findAll()
            .map { it.element.containingFile.name }

        assertContainsElements(usageFiles, "my_module.services.yml")
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
