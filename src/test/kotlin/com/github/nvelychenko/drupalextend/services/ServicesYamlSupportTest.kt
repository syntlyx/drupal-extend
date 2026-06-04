package com.github.nvelychenko.drupalextend.services

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.inspection.ServicesYamlInspection
import com.github.nvelychenko.drupalextend.documentation.ServicesYamlDocumentationProvider
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.DrupalUsageTargetProvider
import com.github.nvelychenko.drupalextend.usage.DrupalImplicitUsageProvider
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

class ServicesYamlSupportTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
    }

    fun testServiceReferenceValueParsing() {
        assertEquals("config.factory", ServicesYaml.serviceIdFromReferenceValue("@config.factory"))
        assertEquals("config.factory", ServicesYaml.serviceIdFromReferenceValue("@?config.factory"))
        assertNull(ServicesYaml.serviceIdFromReferenceValue("@@config.factory"))
        assertNull(ServicesYaml.serviceIdFromReferenceValue("@=service('config.factory')"))
        assertNull(ServicesYaml.serviceIdFromReferenceValue("config.factory"))
    }

    fun testServicesYamlHelperRecognizesDrupalShorthandDefinitions() {
        val yamlFile = myFixture.configureByText(
            "core.services.yml",
            """
            services:
              Drupal\Core\Database\Connection: '@database'
              database.replica:
                class: Drupal\Core\Database\Connection
                factory: Drupal\Core\Database\Database::getConnection
                arguments: [replica, '@database']
              Drupal\Core\Database\EventSubscriber\StatementExecutionSubscriber: ~
            """.trimIndent()
        ) as YAMLFile

        val servicesMapping = servicesMapping(yamlFile)
        val serviceIds = servicesMapping.keyValues
            .filter { ServicesYaml.isServiceDefinition(it) }
            .map { it.keyText }

        assertContainsElements(
            serviceIds,
            "Drupal\\Core\\Database\\Connection",
            "database.replica",
            "Drupal\\Core\\Database\\EventSubscriber\\StatementExecutionSubscriber"
        )

        val replicaDefinition = servicesMapping.keyValues.first { it.keyText == "database.replica" }
        val replicaMapping = replicaDefinition.value as YAMLMapping
        val classScalar = replicaMapping.keyValues.first { it.keyText == "class" }.value as YAMLScalar
        assertEquals("services.database.replica.class", ServicesYaml.serviceKeyPath(classScalar))

        val databaseReference = PsiTreeUtil.collectElementsOfType(yamlFile, YAMLScalar::class.java)
            .first { !ServicesYaml.isKey(it) && it.textValue == "@database" }
        val serviceId = ServicesYaml.serviceIdFromReferenceValue(databaseReference.textValue)!!
        val range = ServicesYaml.referenceValueTextRange(databaseReference, databaseReference.textValue, serviceId)
        assertEquals("database", databaseReference.text.substring(range.startOffset, range.endOffset))
    }

    fun testServicesIndexStoresServiceIdsAndClasses() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              Drupal\Core\Database\Connection: '@database'
              database.replica:
                class: Drupal\Core\Database\Connection
                factory: Drupal\Core\Database\Database::getConnection
                arguments: [replica]
              Drupal\Core\Database\EventSubscriber\StatementExecutionSubscriber: ~
            """.trimIndent()
        )

        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)

        assertEquals(
            "Drupal\\Core\\Database\\Connection",
            index.getValues(SERVICES_INDEX_KEY, "database.replica", scope).firstOrNull()
        )
        assertEquals(
            "",
            index.getValues(SERVICES_INDEX_KEY, "Drupal\\Core\\Database\\Connection", scope).firstOrNull()
        )
        assertEquals(
            "",
            index.getValues(
                SERVICES_INDEX_KEY,
                "Drupal\\Core\\Database\\EventSubscriber\\StatementExecutionSubscriber",
                scope
            ).firstOrNull()
        )
    }

    fun testServiceClassIsImplicitUsage() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Provider.php",
            """
            <?php
            namespace Drupal\my_module;

            class Provider {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              my_module.provider:
                class: Drupal\my_module\Provider
            """.trimIndent()
        )

        val phpClass = PsiTreeUtil.findChildOfType(classFile, PhpClass::class.java)
            ?: error("Expected PHP class")

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(phpClass))
    }

    fun testFqnServiceIdIsImplicitUsage() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Provider.php",
            """
            <?php
            namespace Drupal\my_module;

            class Provider {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              Drupal\my_module\Provider: ~
            """.trimIndent()
        )

        val phpClass = PsiTreeUtil.findChildOfType(classFile, PhpClass::class.java)
            ?: error("Expected PHP class")

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(phpClass))
    }

    fun testFactoryServiceMethodIsImplicitUsage() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/ClientFactory.php",
            """
            <?php
            namespace Drupal\my_module;

            class ClientFactory {
              public function dynamoDbClient() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              aws.client_factory:
                class: Drupal\my_module\ClientFactory
              aws.dynamodb_client:
                class: Aws\DynamoDb\DynamoDbClient
                factory: ['@aws.client_factory', 'dynamoDbClient']
            """.trimIndent()
        )

        val phpClass = PsiTreeUtil.findChildOfType(classFile, PhpClass::class.java)
            ?: error("Expected PHP class")
        val method = phpClass.findMethodByName("dynamoDbClient") ?: error("Expected factory method")

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(method))
    }

    fun testServicesYamlInspectionReportsUnknownReferencesClassesAndDuplicates() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        myFixture.configureByText(
            "problem.services.yml",
            """
            services:
              existing.service:
                class: '%existing.service.class%'
              consumer:
                class: Missing\ClassName
                arguments: ['@missing.service', '@?optional.missing']
              consumer:
                parent: missing.parent
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Duplicate service id 'consumer'",
            "Unknown service 'missing.service'",
            "Unknown service 'missing.parent'",
            "Unknown class 'Missing\\ClassName'"
        )
        assertFalse(descriptions.contains("Unknown service 'optional.missing'"))
    }

    fun testServicesYamlInspectionAcceptsBuiltInServiceContainerAndInterfaces() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Cache/CacheBackendInterface.php",
            """
            <?php
            namespace Drupal\Core\Cache;

            interface CacheBackendInterface {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "core.services.yml",
            """
            services:
              Drupal\Component\DependencyInjection\ReverseContainer:
                arguments: ['@service_container', '@kernel']
              cache.access_policy_memory:
                class: Drupal\Core\Cache\CacheBackendInterface
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Unknown service 'service_container'"))
        assertFalse(descriptions.contains("Unknown service 'kernel'"))
        assertFalse(descriptions.contains("Unknown class 'Drupal\\Core\\Cache\\CacheBackendInterface'"))
    }

    fun testServicesYamlInspectionReportsConstructorArgumentMismatch() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        addSampleServiceClasses()
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.mailer:
                class: Drupal\my_module\Mailer
              sample.consumer_missing:
                class: Drupal\my_module\Consumer
                arguments: ['@logger.channel.sample']
              sample.consumer_too_many:
                class: Drupal\my_module\Consumer
                arguments: ['@logger.channel.sample', '@sample.mailer', '@logger.channel.sample', '@sample.mailer']
              sample.consumer_mismatch:
                class: Drupal\my_module\Consumer
                arguments: ['@sample.mailer', '@logger.channel.sample']
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            $$"Missing constructor argument '$mailer' for service 'sample.consumer_missing'",
            "Too many constructor arguments for service 'sample.consumer_too_many': expected 3, got 4",
            $$"Constructor argument '$logger' for service 'sample.consumer_mismatch' expects 'Drupal\\my_module\\LoggerInterface', 'Drupal\\my_module\\Mailer' provided",
            $$"Constructor argument '$mailer' for service 'sample.consumer_mismatch' expects 'Drupal\\my_module\\Mailer', 'Drupal\\my_module\\Logger' provided"
        )
    }

    fun testServicesYamlInspectionConstructorArgumentMismatchQuickFixReplacesService() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        addSampleServiceClasses()
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.mailer:
                class: Drupal\my_module\Mailer
              sample.consumer:
                class: Drupal\my_module\Consumer
                arguments: ['@sample.<caret>mailer', '@sample.mailer']
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Replace with '@logger.channel.sample'"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.mailer:
                class: Drupal\my_module\Mailer
              sample.consumer:
                class: Drupal\my_module\Consumer
                arguments: ['@logger.channel.sample', '@sample.mailer']
            """.trimIndent()
        )
    }

    fun testServicesYamlInspectionAcceptsMatchingConstructorArgumentsAndSkipsFactoryServices() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        addSampleServiceClasses()
        myFixture.addFileToProject(
            "modules/custom/my_module/src/ConsumerFactory.php",
            """
            <?php
            namespace Drupal\my_module;

            class ConsumerFactory {
              public static function create(): Consumer {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.mailer:
                class: Drupal\my_module\Mailer
              sample.consumer:
                class: Drupal\my_module\Consumer
                arguments: ['@logger.channel.sample', '@sample.mailer']
              sample.factory_consumer:
                class: Drupal\my_module\Consumer
                factory: ['Drupal\my_module\ConsumerFactory', create]
                arguments: []
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.any { it.startsWith("Missing constructor argument") })
        assertFalse(descriptions.any { it.startsWith("Too many constructor arguments") })
        assertFalse(descriptions.any { it.startsWith("Constructor argument") })
    }

    fun testPhpDrupalServiceCompletionSuggestsIndexedServices() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              application.view.manager:
                class: Drupal\my_module\Application\ApplicationView
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function service(string $id): object {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal::service('applica<caret>');
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                <?php

                \Drupal::service('application.view.manager');
                """.trimIndent()
            )
            return
        }

        val lookupStrings = lookupElements
            .map { it.lookupString }

        assertContainsElements(lookupStrings, "application.view.manager")
    }

    fun testYamlServiceCompletionInsertsSingleAtSign() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                arguments: ['@<caret>']
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        val serviceLookup = lookupElements.first { it.lookupString == "@config.factory" }
        myFixture.lookup.currentItem = serviceLookup
        myFixture.finishLookup('\n')

        myFixture.checkResult(
            """
            services:
              consumer:
                arguments: ['@config.factory']
            """.trimIndent()
        )
    }

    fun testYamlServiceCompletionSuggestsBuiltInServiceContainer() {
        myFixture.configureByText(
            "consumer.services.yml",
            """
            services:
              consumer:
                arguments: ['@service_<caret>']
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertContainsElements(lookupStrings, "@service_container")
    }

    fun testYamlServiceReferenceResolvesToServiceDefinition() {
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
              consumer:
                arguments: ['@config.<caret>factory']
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("config.factory", resolvedKey?.keyText)
        assertEquals("config.factory", reference.rangeInElement.substring(reference.element.text))
    }

    fun testYamlServiceFactoryMethodReferenceResolvesToPhpMethod() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/ClientFactory.php",
            """
            <?php
            namespace Drupal\my_module;

            class ClientFactory {
              public function dynamoDbClient() {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              aws.client_factory:
                class: Drupal\my_module\ClientFactory
              aws.dynamodb_client:
                class: Aws\DynamoDb\DynamoDbClient
                factory: ['@aws.client_factory', 'dynamoDb<caret>Client']
            """.trimIndent()
        )

        val resolved = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()

        assertTrue(resolved is Method)
        assertEquals("dynamoDbClient", (resolved as Method).name)
    }

    fun testYamlServiceDocumentationShowsClassAndDeclaration() {
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              application.manager:
                class: Drupal\my_module\Application\ApplicationManager
              consumer:
                arguments: ['@application.<caret>manager']
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = ServicesYamlDocumentationProvider()
        val quickInfo = provider.getQuickNavigateInfo(element, element) ?: ""
        val documentation = provider.generateDoc(element, element) ?: ""

        assertEquals("application.manager -> Drupal\\my_module\\Application\\ApplicationManager", quickInfo)
        assertTrue(documentation, documentation.contains("service application.manager"))
        assertTrue(documentation, documentation.contains("Drupal\\my_module\\Application\\ApplicationManager"))
        assertTrue(documentation, documentation.contains("my_module.services.yml"))
    }

    fun testYamlServiceClassDocumentationUsesPhpStormClassDocumentation() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Application/ApplicationManager.php",
            """
            <?php
            namespace Drupal\my_module\Application;

            /**
             * Coordinates application services.
             */
            class ApplicationManager {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              application.manager:
                class: Drupal\my_module\Application\Application<caret>Manager
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = ServicesYamlDocumentationProvider()
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedClass = reference.resolve() as? PhpClass
        val quickInfo = provider.getQuickNavigateInfo(element, element) ?: ""
        val documentation = provider.generateDoc(element, element) ?: ""

        assertEquals("ApplicationManager", resolvedClass?.name)
        assertTrue(quickInfo, quickInfo.contains("ApplicationManager"))
        assertTrue(documentation, documentation.contains("Coordinates application services."))
    }

    fun testYamlServiceClassKeyDocumentationFallsBackToPhpStorm() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/EventSubscriber.php",
            """
            <?php
            namespace Drupal\my_module;

            class EventSubscriber {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              my_module.event_subscriber:
                cla<caret>ss: Drupal\my_module\EventSubscriber
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = ServicesYamlDocumentationProvider()

        assertNull(provider.getQuickNavigateInfo(element, element))
        assertNull(provider.generateDoc(element, element))
    }

    fun testServiceUsageTargetProviderNormalizesUsageToServiceDefinition() {
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
            "consumer.php",
            """
            <?php

            \Drupal::service('config.factory');
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              config.<caret>factory:
                class: Drupal\Core\Config\ConfigFactory
              consumer:
                arguments: ['@config.factory']
            """.trimIndent()
        )

        val targets = DrupalUsageTargetProvider().getTargets(myFixture.elementAtCaret)
        val targetSummary = targets.joinToString("\n") { it.presentation?.presentableText ?: it.name.orEmpty() }

        assertEquals(targetSummary, 1, targets.size)
        val targetElement = (targets.single() as PsiElement2UsageTargetAdapter).targetElement
        assertEquals("config.factory", PsiTreeUtil.getParentOfType(targetElement, YAMLKeyValue::class.java, false)?.keyText)
    }

    fun testFindUsagesFromServiceDefinitionKeyFindsYamlAndPhpReferences() {
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
            "consumer.php",
            """
            <?php

            \Drupal::service('config.factory');
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              config.<caret>factory:
                class: Drupal\Core\Config\ConfigFactory
              consumer:
                arguments: ['@config.factory']
            """.trimIndent()
        )

        val serviceKey = PsiTreeUtil.getParentOfType(myFixture.elementAtCaret, YAMLKeyValue::class.java, false)
            ?.key
            ?: error("Expected service key")
        val usages = myFixture.findUsages(serviceKey)
        val usageSummary = usages.joinToString("\n") {
            "${it.element?.containingFile?.name}: ${it.element?.text}"
        }

        assertTrue(
            usageSummary,
            usages.any {
                it.element?.containingFile?.name == "my_module.services.yml"
                        && it.element?.text?.contains("@config.factory") == true
            }
        )
        assertTrue(
            usageSummary,
            usages.any {
                it.element?.containingFile?.name == "consumer.php"
                        && it.element?.text?.contains("config.factory") == true
            }
        )
    }

    fun testPhpDrupalServiceReferenceResolvesToServiceDefinition() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function service(string $id): object {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal::service('config.<caret>factory');
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("config.factory", resolvedKey?.keyText)
    }

    fun testPhpContainerGetServiceReferenceResolvesToServiceDefinition() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $container->get('config.<caret>factory');
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("config.factory", resolvedKey?.keyText)
    }

    fun testPhpNonContainerGetDoesNotRegisterServiceReference() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              config.factory:
                class: Drupal\Core\Config\ConfigFactory
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            $configFactory->get('config.<caret>factory');
            """.trimIndent()
        )

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset - 1)
        assertNull(reference)
    }

    fun testYamlClassCompletionSuggestsPhpClasses() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Application/SampleReportGenerator.php",
            """
            <?php
            namespace Drupal\my_module\Application;

            class SampleReportGenerator {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              sample.report.generator:
                class: Sample<caret>
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertContainsElements(
            lookupStrings,
            "Drupal\\my_module\\Application\\SampleReportGenerator"
        )
    }

    fun testYamlClassCompletionPrefersShorterMatchingFqns() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Cache/VariationCacheInterface.php",
            """
            <?php
            namespace Drupal\Core\Cache;

            interface VariationCacheInterface {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Cache/Context/VariationCacheInterface.php",
            """
            <?php
            namespace Drupal\Core\Cache\Context;

            interface VariationCacheInterface {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "core.services.yml",
            """
            services:
              cache.variation:
                class: Variation<caret>
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertTrue(
            lookupStrings.indexOf("Drupal\\Core\\Cache\\VariationCacheInterface") <
                    lookupStrings.indexOf("Drupal\\Core\\Cache\\Context\\VariationCacheInterface")
        )
    }

    fun testYamlClassCompletionPrefersNamespacePrefixMatches() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Cache/VariationCacheInterface.php",
            """
            <?php
            namespace Drupal\Core\Cache;

            interface VariationCacheInterface {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/other/src/VariationCacheInterface.php",
            """
            <?php
            namespace Drupal\other;

            interface VariationCacheInterface {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "core.services.yml",
            """
            services:
              cache.variation:
                class: Drupal\Core\Cache\Var<caret>
            """.trimIndent()
        )

        val lookupStrings = myFixture.complete(CompletionType.BASIC)
            .map { it.lookupString }

        assertContainsElements(lookupStrings, "Drupal\\Core\\Cache\\VariationCacheInterface")
        assertFalse(lookupStrings.contains("Drupal\\other\\VariationCacheInterface"))
    }

    fun testYamlClassCompletionReplacesTypedPrefixAndSuggestsInterfaces() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Cache/VariationCacheInterface.php",
            """
            <?php
            namespace Drupal\Core\Cache;

            interface VariationCacheInterface {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "core.services.yml",
            """
            services:
              cache.variation:
                class: Drupal\Core\Cache\Var<caret>
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        val interfaceLookup = lookupElements.first { it.lookupString == "Drupal\\Core\\Cache\\VariationCacheInterface" }
        myFixture.lookup.currentItem = interfaceLookup
        myFixture.finishLookup('\n')

        myFixture.checkResult(
            """
            services:
              cache.variation:
                class: Drupal\Core\Cache\VariationCacheInterface
            """.trimIndent()
        )
    }

    private fun addSampleServiceClasses() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/LoggerInterface.php",
            """
            <?php
            namespace Drupal\my_module;

            interface LoggerInterface {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Logger.php",
            """
            <?php
            namespace Drupal\my_module;

            class Logger implements LoggerInterface {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Mailer.php",
            """
            <?php
            namespace Drupal\my_module;

            class Mailer {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Consumer.php",
            $$"""
            <?php
            namespace Drupal\my_module;

            class Consumer {
              public function __construct(LoggerInterface $logger, Mailer $mailer, string $label = '') {}
            }
            """.trimIndent()
        )
    }

    private fun servicesMapping(yamlFile: YAMLFile): YAMLMapping {
        return ServicesYaml.servicesMapping(PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java))
            ?: error("Expected services mapping")
    }

    private fun PsiElement.lineNumber(): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
            ?: error("Expected document for ${containingFile.name}")

        return document.getLineNumber(textRange.startOffset) + 1
    }
}
