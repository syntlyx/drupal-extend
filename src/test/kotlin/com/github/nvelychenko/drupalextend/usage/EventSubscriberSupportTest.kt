package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class EventSubscriberSupportTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        addEventSubscriberInterface()
    }

    fun testSimpleMappingHandlerIsImplicitUsage() {
        val file = configureSubscriber(
            $$"""
            public static function getSubscribedEvents(): array {
              return [
                'kernel.request' => 'onRequest',
              ];
            }

            public function onRequest(): void {}
            """
        )
        val phpClass = file.phpClass("MySubscriber")
        val provider = DrupalImplicitUsageProvider()

        assertTrue(provider.isImplicitUsage(phpClass.method("onRequest")))
    }

    fun testArrayMappingHandlerIsImplicitUsage() {
        val file = configureSubscriber(
            $$"""
            public static function getSubscribedEvents(): array {
              return [
                'kernel.request' => ['onSave', 10],
              ];
            }

            public function onSave(): void {}
            """
        )
        val phpClass = file.phpClass("MySubscriber")
        val provider = DrupalImplicitUsageProvider()

        assertTrue(provider.isImplicitUsage(phpClass.method("onSave")))
    }

    fun testNestedArrayMappingHandlerIsImplicitUsage() {
        val file = configureSubscriber(
            $$"""
            public static function getSubscribedEvents(): array {
              return [
                'kernel.request' => [['onFirst', 10], ['onSecond']],
              ];
            }

            public function onFirst(): void {}

            public function onSecond(): void {}
            """
        )
        val phpClass = file.phpClass("MySubscriber")
        val provider = DrupalImplicitUsageProvider()

        assertTrue(provider.isImplicitUsage(phpClass.method("onFirst")))
        assertTrue(provider.isImplicitUsage(phpClass.method("onSecond")))
    }

    fun testUnreferencedMethodIsNotImplicitUsage() {
        val file = configureSubscriber(
            $$"""
            public static function getSubscribedEvents(): array {
              return [
                'kernel.request' => 'onRequest',
              ];
            }

            public function onRequest(): void {}

            public function unrelated(): void {}
            """
        )
        val phpClass = file.phpClass("MySubscriber")
        val provider = DrupalImplicitUsageProvider()

        assertFalse(provider.isImplicitUsage(phpClass.method("unrelated")))
    }

    fun testGetSubscribedEventsItselfIsImplicitUsage() {
        val file = configureSubscriber(
            $$"""
            public static function getSubscribedEvents(): array {
              return [
                'kernel.request' => 'onRequest',
              ];
            }

            public function onRequest(): void {}
            """
        )
        val phpClass = file.phpClass("MySubscriber")
        val provider = DrupalImplicitUsageProvider()

        assertTrue(provider.isImplicitUsage(phpClass.method("getSubscribedEvents")))
    }

    fun testHandlerStringHasReferenceToMethod() {
        val file = configureSubscriber(
            $$"""
            public static function getSubscribedEvents(): array {
              return [
                'kernel.request' => 'onRequest',
              ];
            }

            public function onRequest(): void {}
            """
        )
        val phpClass = file.phpClass("MySubscriber")
        val literal = PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)
            .first { it.contents == "onRequest" }

        val reference = literal.references.firstOrNull { it.resolve() != null }
            ?: error("Expected a resolvable reference on the handler string")

        assertEquals(phpClass.method("onRequest"), reference.resolve())
    }

    private fun configureSubscriber(body: String): PsiFile = myFixture.configureByText(
        "MySubscriber.php",
        $$"""
        <?php

        namespace Drupal\my_module\EventSubscriber;

        use Symfony\Component\EventDispatcher\EventSubscriberInterface;

        class MySubscriber implements EventSubscriberInterface {
        $$body
        }
        """.trimIndent()
    )

    private fun addEventSubscriberInterface() {
        myFixture.addFileToProject(
            "vendor/symfony/event-dispatcher/EventSubscriberInterface.php",
            $$"""
            <?php

            namespace Symfony\Component\EventDispatcher;

            interface EventSubscriberInterface {
              public static function getSubscribedEvents();
            }
            """.trimIndent()
        )
    }

    private fun PsiFile.phpClass(name: String): PhpClass =
        PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .firstOrNull { it.name == name }
            ?: error("Expected PHP class $name")

    private fun PhpClass.method(name: String): Method =
        findMethodByName(name) ?: error("Expected method $name")
}
