package com.github.nvelychenko.drupalextend.services

import com.github.nvelychenko.drupalextend.inspection.ServicesYamlInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FillConstructorArgumentsFixTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
    }

    fun testFillAppendsMatchedServiceReferencesToInlineList() {
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
                arguments: ['@logger.channel.sample']
            """.trimIndent()
        )

        applyFillFix()

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

    fun testFillUsesTodoPlaceholderAndCommentForUnmatchedScalarParameter() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        addScalarConsumerClasses()
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.scalar_consumer:
                class: Drupal\my_module\ScalarConsumer
                arguments: ['@logger.channel.sample']
            """.trimIndent()
        )

        applyFillFix()

        myFixture.checkResult(
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.scalar_consumer:
                class: Drupal\my_module\ScalarConsumer
                arguments: ['@logger.channel.sample', 'TODO']  # TODO: provide remaining arguments
            """.trimIndent()
        )
    }

    fun testFillExtendsEmptyInlineList() {
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
                arguments: []
            """.trimIndent()
        )

        applyFillFix()

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

    fun testFillExtendsBlockList() {
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
                arguments:
                  - '@logger.channel.sample'
            """.trimIndent()
        )

        applyFillFix()

        myFixture.checkResult(
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.mailer:
                class: Drupal\my_module\Mailer
              sample.consumer:
                class: Drupal\my_module\Consumer
                arguments:
                  - '@logger.channel.sample'
                  - '@sample.mailer'
            """.trimIndent()
        )
    }

    fun testFillCreatesArgumentsKeyWhenAbsent() {
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
            """.trimIndent()
        )

        applyFillFix()

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

    fun testFillNotOfferedWhenArgumentCountsMatch() {
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
                arguments: ['@logger.channel.sample', '@sample.mailer']
            """.trimIndent()
        )

        myFixture.doHighlighting()
        assertTrue(
            "Fill fix must not be offered when arguments already satisfy the constructor",
            myFixture.getAllQuickFixes().none { it.text == "Fill constructor arguments" }
        )
    }

    fun testFillIsIdempotentWhenAppliedTwice() {
        myFixture.enableInspections(ServicesYamlInspection::class.java)
        addScalarConsumerClasses()
        myFixture.configureByText(
            "my_module.services.yml",
            """
            services:
              logger.channel.sample:
                class: Drupal\my_module\Logger
              sample.scalar_consumer:
                class: Drupal\my_module\ScalarConsumer
                arguments: ['@logger.channel.sample']
            """.trimIndent()
        )

        applyFillFix()

        val afterFirst = myFixture.file.text

        // A second highlighting pass should not offer the fill fix again — the gap is closed.
        myFixture.doHighlighting()
        assertTrue(
            "Fill fix must not be offered after the gap is closed",
            myFixture.getAllQuickFixes().none { it.text == "Fill constructor arguments" }
        )
        assertEquals(afterFirst, myFixture.file.text)
    }

    private fun applyFillFix() {
        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Fill constructor arguments"
        }
        myFixture.launchAction(intention)
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
              public function __construct(LoggerInterface $logger, Mailer $mailer) {}
            }
            """.trimIndent()
        )
    }

    private fun addScalarConsumerClasses() {
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
            "modules/custom/my_module/src/ScalarConsumer.php",
            $$"""
            <?php
            namespace Drupal\my_module;

            class ScalarConsumer {
              public function __construct(LoggerInterface $logger, string $label) {}
            }
            """.trimIndent()
        )
    }
}
