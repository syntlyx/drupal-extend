package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertyNameRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStylePropertyNameEnabled = true
        // Disable every other rule so highlights are unambiguous.
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMemberVisibilityEnabled = false
        project.drupalExtendSettings.isCodeStyleConstantCasingEnabled = false
        project.drupalExtendSettings.isCodeStyleClosingPhpTagEnabled = false
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = false
        project.drupalExtendSettings.isCodeStyleShortArraySyntaxEnabled = false
        project.drupalExtendSettings.isCodeStyleMultilineArrayStyleEnabled = false
        project.drupalExtendSettings.isCodeStyleElseIfSyntaxEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleBlockBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleCastSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleUseStatementSortingEnabled = false
        project.drupalExtendSettings.isCodeStyleFullyQualifiedClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionIndentationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionRequiredEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionLineEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockApiTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleElsePlacementEnabled = false
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = false
        project.drupalExtendSettings.isCodeStyleInlineCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleFileDocCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = false
        project.drupalExtendSettings.isCodeStyleScopeKeywordSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
    }

    // -------------------------------------------------------------------------
    // snake_case property: flagged
    // -------------------------------------------------------------------------

    fun testSnakeCasePropertyIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              protected ${'$'}entity_type_manager;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Class property \$entity_type_manager should use lowerCamelCase"
        )
    }

    // -------------------------------------------------------------------------
    // lowerCamelCase property: silent
    // -------------------------------------------------------------------------

    fun testLowerCamelCasePropertyIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              protected ${'$'}entityTypeManager;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "lowerCamelCase property must not be flagged",
            descriptions.any { it.contains("should use lowerCamelCase") }
        )
    }

    // -------------------------------------------------------------------------
    // Single lowercase word: silent
    // -------------------------------------------------------------------------

    fun testSingleLowercaseWordPropertyIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public ${'$'}items;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Single lowercase word property must not be flagged",
            descriptions.any { it.contains("should use lowerCamelCase") }
        )
    }

    // -------------------------------------------------------------------------
    // Uppercase-first property: flagged
    // -------------------------------------------------------------------------

    fun testUppercaseFirstPropertyIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public ${'$'}Foo;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Class property \$Foo should use lowerCamelCase")
    }

    // -------------------------------------------------------------------------
    // Leading underscore: flagged (Drupal CS violation)
    // -------------------------------------------------------------------------

    fun testLeadingUnderscorePropertyIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              private ${'$'}_internalValue;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Class property \$_internalValue should use lowerCamelCase")
    }

    // -------------------------------------------------------------------------
    // Multiple fields in one declaration: each checked individually
    // -------------------------------------------------------------------------

    fun testMultipleFieldsInOneDeclarationAreEachChecked() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              protected ${'$'}entity_type, ${'$'}goodName, ${'$'}Bad_field;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Class property \$entity_type should use lowerCamelCase",
            "Class property \$Bad_field should use lowerCamelCase"
        )
        assertFalse(
            "goodName must not be flagged in a multi-field declaration",
            descriptions.any { it.contains("\$goodName should use lowerCamelCase") }
        )
    }

    // -------------------------------------------------------------------------
    // Constants: not flagged (even if they look like snake_case)
    // -------------------------------------------------------------------------

    fun testConstantsAreNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public const SOME_CONSTANT = 'value';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Constants must never be flagged by the property name rule",
            descriptions.any { it.contains("should use lowerCamelCase") }
        )
    }

    // -------------------------------------------------------------------------
    // Local variables: not flagged (rule targets PhpClassFieldsList only)
    // -------------------------------------------------------------------------

    fun testLocalVariablesAreNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_function(): void {
              ${'$'}some_local = 'value';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Local variables must not be flagged by the property name rule",
            descriptions.any { it.contains("should use lowerCamelCase") }
        )
    }

    // -------------------------------------------------------------------------
    // Parameters: not flagged (rule targets PhpClassFieldsList only)
    // -------------------------------------------------------------------------

    fun testParametersAreNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public function doSomething(string ${'$'}some_param): void {}
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Parameters must not be flagged by the property name rule",
            descriptions.any { it.contains("should use lowerCamelCase") }
        )
    }

    // -------------------------------------------------------------------------
    // Highlight is on the property name element, not the whole declaration
    // -------------------------------------------------------------------------

    fun testHighlightCoversOnlyPropertyName() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              protected ${'$'}entity_type_manager;
            }
            """.trimIndent()
        )

        val highlight = myFixture.doHighlighting().single {
            it.description == "Class property \$entity_type_manager should use lowerCamelCase"
        }

        val highlightedText = myFixture.file.text.substring(highlight.startOffset, highlight.endOffset)
        // The name element includes the leading $ in PHP PSI.
        assertTrue(
            "Highlight must cover the property name, got: '$highlightedText'",
            highlightedText.contains("entity_type_manager")
        )
    }
}
