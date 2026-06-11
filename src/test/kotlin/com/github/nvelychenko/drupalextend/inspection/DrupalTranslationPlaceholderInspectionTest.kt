package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalTranslationPlaceholderInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        myFixture.enableInspections(DrupalTranslationPlaceholderInspection())

        // Minimal stubs so the covered call shapes parse and resolve names.
        myFixture.addFileToProject(
            "stubs.php",
            """
            <?php

            function t(${'$'}string, array ${'$'}args = []) {}

            namespace Drupal\Core\StringTranslation {
                class TranslatableMarkup {
                    public function __construct(${'$'}string, array ${'$'}args = []) {}
                }
                trait StringTranslationTrait {
                    public function t(${'$'}string, array ${'$'}args = []) {}
                    public function formatPlural(${'$'}count, ${'$'}singular, ${'$'}plural, array ${'$'}args = []) {}
                }
            }
            """.trimIndent()
        )
    }

    fun testMissingPlaceholderArgumentIsFlagged() {
        val descriptions = highlight(
            """
            <?php
            t('Hello @name', []);
            """.trimIndent()
        )
        assertTrue(
            "expected missing-argument warning, got: $descriptions",
            descriptions.any { it == "Placeholder '@name' has no replacement argument" }
        )
    }

    fun testUnusedArgumentIsFlagged() {
        val descriptions = highlight(
            """
            <?php
            t('Hello world', ['@name' => 'x']);
            """.trimIndent()
        )
        assertTrue(
            "expected unused-argument warning, got: $descriptions",
            descriptions.any { it == "Replacement argument '@name' is not used in the string" }
        )
    }

    fun testBadPrefixIsFlagged() {
        val descriptions = highlight(
            """
            <?php
            t('Hello world', ['name' => 'x']);
            """.trimIndent()
        )
        assertTrue(
            "expected bad-prefix warning, got: $descriptions",
            descriptions.any { it == "Replacement argument 'name' must start with @, % or :" }
        )
    }

    fun testCorrectCallIsSilent() {
        val descriptions = highlight(
            """
            <?php
            t('Hello @name and %place', ['@name' => 'a', '%place' => 'b']);
            """.trimIndent()
        )
        assertEmpty(descriptions)
    }

    fun testColonUrlPlaceholderWorks() {
        val descriptions = highlight(
            """
            <?php
            t('Visit :url now', [':url' => 'a']);
            """.trimIndent()
        )
        assertEmpty(descriptions)
    }

    fun testThisTraitMethodShapeWorks() {
        val descriptions = highlight(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\StringTranslation\StringTranslationTrait;

            class Foo {
                use StringTranslationTrait;

                public function build() {
                    return ${'$'}this->t('Hi @who', []);
                }
            }
            """.trimIndent()
        )
        assertTrue(
            "expected warning for \$this->t(), got: $descriptions",
            descriptions.any { it == "Placeholder '@who' has no replacement argument" }
        )
    }

    fun testTranslatableMarkupWorks() {
        val descriptions = highlight(
            """
            <?php

            use Drupal\Core\StringTranslation\TranslatableMarkup;

            ${'$'}m = new TranslatableMarkup('Hello @name', []);
            """.trimIndent()
        )
        assertTrue(
            "expected warning for TranslatableMarkup, got: $descriptions",
            descriptions.any { it == "Placeholder '@name' has no replacement argument" }
        )
    }

    fun testFormatPluralCountNeverFlaggedAsMissing() {
        val descriptions = highlight(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\StringTranslation\StringTranslationTrait;

            class Foo {
                use StringTranslationTrait;

                public function build() {
                    return ${'$'}this->formatPlural(${'$'}n, '@count item', '@count items', []);
                }
            }
            """.trimIndent()
        )
        assertFalse(
            "@count must never be reported missing, got: $descriptions",
            descriptions.any { it.contains("@count") }
        )
    }

    fun testFormatPluralCountNeverFlaggedAsUnused() {
        val descriptions = highlight(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\StringTranslation\StringTranslationTrait;

            class Foo {
                use StringTranslationTrait;

                public function build() {
                    return ${'$'}this->formatPlural(${'$'}n, 'one thing', 'many things', ['@count' => ${'$'}n]);
                }
            }
            """.trimIndent()
        )
        assertFalse(
            "@count must never be reported unused, got: $descriptions",
            descriptions.any { it.contains("@count") }
        )
    }

    fun testVariableStringIsSkipped() {
        val descriptions = highlight(
            """
            <?php
            ${'$'}msg = 'Hello @name';
            t(${'$'}msg, []);
            """.trimIndent()
        )
        assertEmpty(descriptions)
    }

    fun testVariableArgsArrayIsSkipped() {
        val descriptions = highlight(
            """
            <?php
            ${'$'}args = ['@name' => 'x'];
            t('Hello @name', ${'$'}args);
            """.trimIndent()
        )
        assertEmpty(descriptions)
    }

    private fun highlight(content: String): List<String> {
        myFixture.configureByText("test.php", content)
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter {
                it.startsWith("Placeholder '") || it.startsWith("Replacement argument '")
            }
    }
}
