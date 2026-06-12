package com.github.nvelychenko.drupalextend.services

import com.github.nvelychenko.drupalextend.inspection.ServicesYamlInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome
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

    /**
     * The Ctrl+Click outcome the goto-or-usages handler would produce at the caret. SU (Show
     * Usages) is the contract for every tag-name position: GTD means a dead in-place navigation
     * — the exact regression the self-resolving reference used to cause.
     */
    private fun gtduOutcomeAtCaret(): GTDUOutcome? =
        GotoDeclarationOrUsageHandler2.testGTDUOutcome(myFixture.editor, myFixture.file, myFixture.caretOffset)

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

        assertEquals(
            "Ctrl+Click on the consumer tag name must take the Show Usages branch",
            GTDUOutcome.SU,
            gtduOutcomeAtCaret()
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

    fun testTaggedIteratorMappingFormGoesThroughShowUsages() {
        addTaggedServices()
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                class: Drupal\consumer\Consumer
                arguments:
                  - !tagged_iterator { tag: my_module.collec<caret>tor }
            """.trimIndent()
        )

        assertEquals(
            "Ctrl+Click on the mapping-form consumer tag must take the Show Usages branch",
            GTDUOutcome.SU,
            gtduOutcomeAtCaret()
        )
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
        assertEquals(
            "Ctrl+Click on the tag declaration must take the Show Usages branch",
            GTDUOutcome.SU,
            gtduOutcomeAtCaret()
        )

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

    fun testTagPositionCarriesNoReferenceAndExcludesSelfFromUsages() {
        // Tag positions are declarations, not references: a self-resolving reference at the
        // caret makes the goto-or-usages handler "navigate" in place instead of showing usages.
        // The clicked site itself must also not come back as its own usage.
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

        val scalar = ServicesYaml.tagPositionScalar(
            myFixture.file.findElementAt(myFixture.caretOffset)!!
        )!!
        assertEmpty("Tag positions must not carry references — they hijack Ctrl+Click", scalar.references.toList())
        assertEquals(
            "Ctrl+Click on a tag declaration must take the Show Usages branch",
            GTDUOutcome.SU,
            gtduOutcomeAtCaret()
        )

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

        assertEquals(
            "Ctrl+Click on the service_collector tag value must take the Show Usages branch",
            GTDUOutcome.SU,
            gtduOutcomeAtCaret()
        )

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
