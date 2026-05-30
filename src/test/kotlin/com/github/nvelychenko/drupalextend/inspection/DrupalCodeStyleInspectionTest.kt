package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalCodeStyleInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = true
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = true
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = true
        project.drupalExtendSettings.isCodeStyleConstantCasingEnabled = true
        project.drupalExtendSettings.isCodeStyleClosingPhpTagEnabled = true
        project.drupalExtendSettings.isCodeStyleShortArraySyntaxEnabled = true
        project.drupalExtendSettings.isCodeStyleElseIfSyntaxEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = true
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleUseStatementSortingEnabled = true
        project.drupalExtendSettings.isCodeStyleFullyQualifiedClassNameEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionIndentationEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionRequiredEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionLineEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionPunctuationEnabled = true
    }

    fun testReportsDrupalClassFunctionAndSpacingProblems() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "test2.php",
            """
            <?php

            class Test2 {
              public function test() {
                return 'test2';
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Class name doesn't match filename; expected \"class test2\"",
            "Missing class doc comment",
            "Missing function doc comment",
            "Expected 1 blank line before function; 0 found",
            "Expected 1 blank line after function; 0 found",
            "The closing brace for the class must have an empty line before it"
        )
    }

    fun testClassFileNameRuleHighlightsOnlyClassName() {
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "test2.php",
            """
            <?php

            class Test2 {}
            """.trimIndent()
        )

        val highlight = myFixture.doHighlighting().single {
            it.description == "Class name doesn't match filename; expected \"class test2\""
        }

        assertEquals("Test2", myFixture.file.text.substring(highlight.startOffset, highlight.endOffset))
    }

    fun testReportsClassFunctionAndMethodNamingProblems() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionIndentationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionRequiredEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionLineEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class test_service {
              public function Bad_method(): void {}

              public function __toString(): string {
                return '';
              }
            }

            function badFunction(): void {}
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val descriptions = highlights.mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Class name should use PascalCase",
            "Method name should use camelCase",
            "Function name should use snake_case"
        )
        val classNameHighlight = highlights.single { it.description == "Class name should use PascalCase" }
        val methodNameHighlight = highlights.single { it.description == "Method name should use camelCase" }
        val functionNameHighlight = highlights.single { it.description == "Function name should use snake_case" }
        assertEquals(
            "test_service",
            myFixture.file.text.substring(classNameHighlight.startOffset, classNameHighlight.endOffset)
        )
        assertEquals(
            "Bad_method",
            myFixture.file.text.substring(methodNameHighlight.startOffset, methodNameHighlight.endOffset)
        )
        assertEquals(
            "badFunction",
            myFixture.file.text.substring(functionNameHighlight.startOffset, functionNameHighlight.endOffset)
        )
        assertFalse(highlights.any {
            it.description == "Method name should use camelCase" &&
            myFixture.file.text.substring(it.startOffset, it.endOffset) == "__toString"
        })
    }

    fun testQuickFixNormalizesDrupalConstantCasing() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(): array {
                return [true, false, null];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(descriptions, "TRUE, FALSE, and NULL constants must be uppercase")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(): array {
                return [TRUE, FALSE, NULL];
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixRemovesClosingPhpTagAtEndOfFile() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
            }
            ?>
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(descriptions, "PHP-only files must not end with a closing tag")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
            }
            """.trimIndent()
        )
    }

    fun testFunctionSpacingRulesHighlightOnlyFunctionName() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionIndentationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionRequiredEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionLineEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function one(): void {}
              public function two(): void {}
            }
            """.trimIndent()
        )

        val highlightedTexts = myFixture.doHighlighting().filter {
            it.description == "Expected 1 blank line before function; 0 found"
        }.map {
            myFixture.file.text.substring(it.startOffset, it.endOffset)
        }

        assertContainsElements(highlightedTexts, "one", "two")
    }

    fun testClassCloseBraceSpacingRuleHighlightsOnlyCloseBrace() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionIndentationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionRequiredEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionLineEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function one(): void {}
            }
            """.trimIndent()
        )

        val highlight = myFixture.doHighlighting().single {
            it.description == "The closing brace for the class must have an empty line before it"
        }

        assertEquals("}", myFixture.file.text.substring(highlight.startOffset, highlight.endOffset))
    }

    fun testQuickFixAddsDocBlockSummaryPunctuation() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Builds the vehicle list<caret>
             */
            final class Example {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Add DocBlock summary punctuation"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Builds the vehicle list.
             */
            final class Example {}
            """.trimIndent()
        )
    }

    fun testQuickFixAddsParamAndReturnDescriptionPunctuation() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Test method description
               *
               * @param string ${'$'}name
               *   hylla
               *
               * @return string
               *   helooa
               */
              public function test(string ${'$'}name): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "DocBlock summary should end with punctuation",
            "@param description should end with punctuation",
            "@return description should end with punctuation"
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal DocBlock"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Test method description.
               *
               * @param string ${'$'}name
               *   hylla.
               *
               * @return string
               *   helooa.
               */
              public function test(string ${'$'}name): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )
    }

    fun testDocBlockSummaryPunctuationIgnoresTagDescriptions() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * @param string ${'$'}name
               *   The name
               */
              public function test(string ${'$'}name): void {}
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("DocBlock summary should end with punctuation"))
        assertContainsElements(
            descriptions,
            "DocBlock is missing a short summary",
            "@param description should end with punctuation"
        )
    }

    fun testQuickFixInsertsBlankLineBeforeDocBlockTags() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            $$"""
            <?php

            final class Example {
              /**
               * Builds the vehicle list.
               * @param string $id
               *   The vehicle ID.
               */
              public function build(string $id): array {}
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Insert blank DocBlock line before tags"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            $$"""
            <?php

            final class Example {
              /**
               * Builds the vehicle list.
               *
               * @param string $id
               *   The vehicle ID.
               */
              public function build(string $id): array {}
            }
            """.trimIndent()
        )
    }

    fun testMissingDocCommentOffersPhpStormGeneratePhpDocFix() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class <caret>Example {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        assertTrue(myFixture.getAllQuickFixes().any {
            it.text.contains("Generate") && it.text.contains("PHPDoc")
        })
        assertFalse(myFixture.getAllQuickFixes().any {
            it.text == "Fix Drupal code style in file"
        })
    }

    fun testMissingFunctionDocCommentDoesNotOfferNoOpFileFix() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "ApplicationActionManager.php",
            """
            <?php

            class ApplicationActionManager {

              public function __construct(
                private readonly iterable ${'$'}actions,
                private readonly LockAction ${'$'}lockAction,
              ) {}

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        assertFalse(myFixture.getAllQuickFixes().any {
            it.text == "Fix Drupal code style in file"
        })
        assertTrue(myFixture.getAllQuickFixes().any {
            it.text.contains("Generate") && it.text.contains("PHPDoc")
        })
    }

    fun testQuickFixNormalizesBlankLineAfterFunction() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function <caret>one(): void {
              }
              public function two(): void {
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAvailableIntention("Normalize blank line after function")
            ?: error("Normalize blank line after function intention is not available")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function one(): void {
              }

              public function two(): void {
              }
            }
            """.trimIndent()
        )
    }

    fun testQuickFixInsertsBlankLineBeforeClassClosingBrace() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function one(): void {
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Insert blank line before class closing brace"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function one(): void {
              }

            }
            """.trimIndent()
        )
    }

    fun testDisabledDocBlockSummaryPunctuationRuleDoesNotReport() {
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Builds the vehicle list
             */
            final class Example {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("DocBlock summary should end with punctuation"))
    }

    fun testDocBlockSummaryPunctuationChecksLastSummaryLine() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Invokes the method to update the application stage and returns an array
               * containing the event and data related to the updated stage.
               *
               * @param \Drupal\nll_core\Application\Application ${'$'}application
               *   The application to update the stage for.
               *
               * @return array
               *   The event and data related to the updated stage.
               */
              public function invoke(Application ${'$'}application): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("DocBlock summary should end with punctuation"))
    }

    fun testReportsDrupalDocBlockProblems() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /** Builds rows.
               *
               * @param string The ID.
               * The ID description.
               * @return array
               * Rows keyed by ID.
               */
              public function build(string ${'$'}id): array {
                return [];
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "DocBlock opening line must not contain content",
            "@param tag is missing parameter name '${'$'}id'",
            "@param description must be indented by two spaces",
            "@return description must be indented by two spaces"
        )
    }

    fun testReportsMissingDocBlockSummary() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * @param string ${'$'}id
               *   The ID.
               */
              public function build(string ${'$'}id): void {}
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "DocBlock is missing a short summary")
    }

    fun testReportsMissingParamAndReturnDescriptions() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Test method description.
               *
               * @param string ${'$'}name
               *
               * @return string
               */
              public function test(string ${'$'}name): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "@param tag is missing description",
            "@return tag is missing description"
        )
    }

    fun testGenericParamTypeDoesNotReportMissingParamName() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Converts parents to an input name.
               *
               * @param array<int, string> ${'$'}parents
               *   Array of form element parent names.
               *
               * @return string
               *   The formatted input name.
               */
              public static function toFormInputName(array ${'$'}parents): string {
                return '';
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@param tag is missing parameter name '${'$'}parents'"))
    }

    fun testArrayShapeReturnTypeDoesNotReportInlineDescription() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Returns applicant phone info.
               *
               * @param string ${'$'}type
               *   Field type.
               *
               * @return array{type: string|null, number: string|null}
               *   Phone info.
               */
              public function getPhone(string ${'$'}type = 'day'): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@return description must be on a continuation line"))
        assertFalse(descriptions.contains("@return tag is missing description"))
    }

    fun testMultilineArrayShapeReturnTypeDoesNotTreatTypeLinesAsDescription() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair for the given client.
               *
               * @param \Drupal\nll_core\OAuth\Client\ClientContext ${'$'}client
               *   The authenticated client context.
               * @param bool ${'$'}silent
               *   Whether to suppress log messages.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string,
               *   expires_in: int,
               *   token_type: string
               * }
               *   Token response array containing access and refresh tokens.
               */
              public function generate(ClientContext ${'$'}client, bool ${'$'}silent = FALSE): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@return description must be indented by two spaces"))
        assertFalse(descriptions.contains("@return description must be on a continuation line"))
        assertFalse(descriptions.contains("@return tag is missing description"))
    }

    fun testQuickFixAddsPunctuationToMultilineArrayShapeReturnDescription() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               *   Token response array
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Add @return description punctuation"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               *   Token response array.
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesMultilineArrayShapeReturnDescriptionIndentation() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               * Token response array.
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Normalize @return description indentation"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               *   Token response array.
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testMultilineArrayShapeReturnTypeWithoutDescriptionReportsMissingDescription() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "@return tag is missing description")
    }

    fun testQuickFixFixesFullDocBlockWithMultilineArrayShapeReturnType() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               * Token response array
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal DocBlock"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Generates a new token pair.
               *
               * @return array{
               *   access_token: string,
               *   refresh_token: string
               * }
               *   Token response array.
               */
              public function generate(): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testDocBlockDescriptionListIndentationAllowsNestedListItems() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Returns a part of the name.
               *
               * @param string ${'$'}part
               *   Name part.
               *   Available options:
               *     - first
               *     - middle
               *     - last
               *     - suffix.
               *
               * @return string
               *   Path of the name.
               */
              public function getName(string ${'$'}part): string {
                return '';
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@param description must be indented by two spaces"))
    }

    fun testQuickFixInsertsMissingParamNameAfterGenericParamType() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Converts parents to an input name.
               *
               * @param array<int, string>
               *   Array of form element parent names.
               */
              public static function toFormInputName(array ${'$'}parents): string {
                return '';
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Insert missing @param name"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Converts parents to an input name.
               *
               * @param array<int, string> ${'$'}parents
               *   Array of form element parent names.
               */
              public static function toFormInputName(array ${'$'}parents): string {
                return '';
              }

            }
            """.trimIndent()
        )
    }

    fun testInlineVarDocBlockDoesNotRequireSummary() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function updateEntity(EntityInterface ${'$'}entity, array ${'$'}data): void {
                /** @var \Drupal\node\Entity\Node|\Drupal\paragraphs\Entity\Paragraph ${'$'}entity */
                ${'$'}skip_fields = ['node.loan.field_loan_institution'];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("DocBlock opening line must not contain content"))
        assertFalse(descriptions.contains("DocBlock is missing a short summary"))
    }

    fun testQuickFixMovesDocBlockOpeningContentToSummaryLine() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /** Builds rows. */
            final class Example {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Move DocBlock summary to its own line"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Builds rows.
             */
            final class Example {}
            """.trimIndent()
        )
    }

    fun testQuickFixInsertsMissingParamName() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string
               *   The ID.
               */
              public function build(string ${'$'}id): void {}
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Insert missing @param name"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string ${'$'}id
               *   The ID.
               */
              public function build(string ${'$'}id): void {}
            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesParamAndReturnDescriptionIndentation() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string ${'$'}id
               * The ID.
               * @return array
               * Rows keyed by ID.
               */
              public function build(string ${'$'}id): array {
                return [];
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Normalize @param description indentation"
        })
        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Normalize @return description indentation"
        })

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string ${'$'}id
               *   The ID.
               * @return array
               *   Rows keyed by ID.
               */
              public function build(string ${'$'}id): array {
                return [];
              }
            }
            """.trimIndent()
        )
    }

    fun testQuickFixMovesInlineParamAndReturnDescriptionsToContinuationLines() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string ${'$'}id The ID.
               * @return array Rows keyed by ID.
               */
              public function build(string ${'$'}id): array {
                return [];
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Move @param description to continuation line"
        })
        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Move @return description to continuation line"
        })

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string ${'$'}id
               *   The ID.
               * @return array
               *   Rows keyed by ID.
               */
              public function build(string ${'$'}id): array {
                return [];
              }
            }
            """.trimIndent()
        )
    }

    fun testQuickFixMovesInlineArrayShapeReturnDescriptionToContinuationLine() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Returns applicant phone info.
               *
               * @return array{type: string|null, number: string|null} Phone info.
               */
              public function getPhone(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Move @return description to continuation line"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Returns applicant phone info.
               *
               * @return array{type: string|null, number: string|null}
               *   Phone info.
               */
              public function getPhone(): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixInsertsBlankLineBetweenParamAndReturnTagGroups() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Test method description.
               *
               * @param string ${'$'}name
               * @return string
               */
              public function test(string ${'$'}name): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Insert blank line between DocBlock tag groups"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Test method description.
               *
               * @param string ${'$'}name
               *
               * @return string
               */
              public function test(string ${'$'}name): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesRedundantDocBlockBlankLines() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Test method description.
               *
               * @return string
               * ${" "}
               * ${" "}
               */
              public function test(): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize DocBlock blank lines"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Test method description.
               *
               * @return string
               */
              public function test(): string {
                return 'test2';
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesFullDrupalDocBlock() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /** Builds rows
               * @param string The ID.
               * Extra ID details.
               * @return array Rows keyed by ID.
               * ${" "}
               */
              public function build(string ${'$'}id): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal DocBlock"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @param string ${'$'}id
               *   The ID.
               *   Extra ID details.
               *
               * @return array
               *   Rows keyed by ID.
               */
              public function build(string ${'$'}id): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesDrupalDocBlocksInFile() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /** Builds rows
               * @return array Rows.
               */
              public function build(): array {
                return [];
              }

              /**
               * Formats name
               * @param string ${'$'}name Name.
               */
              public function format(string ${'$'}name): string {
                return ${'$'}name;
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @return array
               *   Rows.
               */
              public function build(): array {
                return [];
              }

              /**
               * Formats name.
               *
               * @param string ${'$'}name
               *   Name.
               */
              public function format(string ${'$'}name): string {
                return ${'$'}name;
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesDrupalCodeStyleInFile() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /** Builds rows
               * @return array Rows.
               */
              public function build(): array {
                return array('value');
              }
              public function format(): string {
                return 'value';
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {

              /**
               * Builds rows.
               *
               * @return array
               *   Rows.
               */
              public function build(): array {
                return ['value'];
              }

              public function format(): string {
                return 'value';
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixConvertsArrayToShortSyntaxUsingPhpStormFix() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(): array {
              return array('value', array('nested'));
            }
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        assertContainsElements(
            highlights.mapNotNull { it.description },
            "array() literals must use short array syntax"
        )

        val highlight = highlights.single {
            it.description == "array() literals must use short array syntax"
        }
        assertEquals("array", myFixture.file.text.substring(highlight.startOffset, highlight.endOffset))

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Convert array to short syntax"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(): array {
              return ['value', ['nested']];
            }
            """.trimIndent()
        )
    }

    fun testQuickFixConvertsElseIfToElseif() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(bool ${'$'}value): string {
              if (${'$'}value) {
                return 'yes';
              } else if (!${'$'}value) {
                return 'no';
              }
              return 'unknown';
            }
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        assertContainsElements(
            highlights.mapNotNull { it.description },
            "else if constructs must use elseif"
        )

        val highlight = highlights.single {
            it.description == "else if constructs must use elseif"
        }
        assertEquals("else if", myFixture.file.text.substring(highlight.startOffset, highlight.endOffset))

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Convert else if to elseif"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(bool ${'$'}value): string {
              if (${'$'}value) {
                return 'yes';
              } elseif (!${'$'}value) {
                return 'no';
              }
              return 'unknown';
            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesElseIfSyntaxInFile() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(bool ${'$'}value): string {
              if (${'$'}value) {
                return 'yes';
              } else if (!${'$'}value) {
                return 'no';
              }
              return 'unknown';
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(bool ${'$'}value): string {
              if (${'$'}value) {
                return 'yes';
              } elseif (!${'$'}value) {
                return 'no';
              }
              return 'unknown';
            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesAssignmentAndArrayOperatorSpacing() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(string ${'$'}id): array {
                ${'$'}rows=['id'=>${'$'}id];
                ${'$'}count+=1;
                return ${'$'}rows;
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Assignment operator must be surrounded by single spaces",
            "Array key/value operator must be surrounded by single spaces"
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(string ${'$'}id): array {
                ${'$'}rows = ['id' => ${'$'}id];
                ${'$'}count += 1;
                return ${'$'}rows;
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesBinaryOperatorSpacing() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(int ${'$'}left, int ${'$'}right): string {
                return ${'$'}left+${'$'}right . ':' . (${'$'}left===${'$'}right ? 'yes' : 'no');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(descriptions, "Binary operator must be surrounded by single spaces")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(int ${'$'}left, int ${'$'}right): string {
                return ${'$'}left + ${'$'}right . ':' . (${'$'}left === ${'$'}right ? 'yes' : 'no');
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesCommaSpacing() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(string ${'$'}id,string ${'$'}label): array {
                return ${'$'}this->format(['id' ,${'$'}id,'label'  , ${'$'}label],${'$'}label);
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(descriptions, "Comma-separated lists must use single spaces")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(string ${'$'}id, string ${'$'}label): array {
                return ${'$'}this->format(['id', ${'$'}id, 'label', ${'$'}label], ${'$'}label);
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixNormalizesControlStructureSpacing() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(array ${'$'}items): array {
                foreach(${'$'}items as${'$'}key=>${'$'}value){
                  if(${'$'}key){
                    ${'$'}rows[] = ${'$'}value;
                  }elseif(${'$'}value){
                    ${'$'}rows[] = ${'$'}key;
                  }else{
                    ${'$'}rows[] = 'empty';
                  }
                }
                while(${'$'}items){
                }
                switch(${'$'}items){
                }
                return ${'$'}rows;
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(descriptions, "Control structures must use Drupal spacing")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(array ${'$'}items): array {
                foreach (${'$'}items as ${'$'}key => ${'$'}value) {
                  if (${'$'}key) {
                    ${'$'}rows[] = ${'$'}value;
                  } elseif (${'$'}value) {
                    ${'$'}rows[] = ${'$'}key;
                  } else {
                    ${'$'}rows[] = 'empty';
                  }
                }
                while (${'$'}items) {
                }
                switch (${'$'}items) {
                }
                return ${'$'}rows;
              }

            }
            """.trimIndent()
        )
    }

    fun testControlStructureSpacingAllowsForeachByReferenceValue() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(array ${'$'}array): void {
                foreach (${'$'}array as ${'$'}key => &${'$'}value) {
                  ${'$'}value = ${'$'}key;
                }
                foreach (${'$'}array as &${'$'}value) {
                  ${'$'}value = 'changed';
                }
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Control structures must use Drupal spacing"))
    }

    fun testQuickFixNormalizesForeachByReferenceValueSpacing() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(array ${'$'}array): void {
                foreach(${'$'}array as${'$'}key=>& ${'$'}value){
                  ${'$'}value = ${'$'}key;
                }
                foreach(${'$'}array as & ${'$'}value){
                  ${'$'}value = 'changed';
                }
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(array ${'$'}array): void {
                foreach (${'$'}array as ${'$'}key => &${'$'}value) {
                  ${'$'}value = ${'$'}key;
                }
                foreach (${'$'}array as &${'$'}value) {
                  ${'$'}value = 'changed';
                }
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixSortsUseStatements() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            use Drupal\zeta\Zed;
            use Drupal\alpha\Alpha;

            final class Example {
              public function build(): Alpha {
                return new Alpha();
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        assertContainsElements(descriptions, "Use statements must be sorted alphabetically")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Sort use statements"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            use Drupal\alpha\Alpha;
            use Drupal\zeta\Zed;

            final class Example {
              public function build(): Alpha {
                return new Alpha();
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixImportsFullyQualifiedClassName() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function cache(): \Drupal\Core\Cache\CacheBackendInterface {
                return \Drupal::cache();
              }

            }
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        assertContainsElements(
            highlights.mapNotNull { it.description },
            "Fully qualified class names must be imported with use statements"
        )
        val highlight = highlights.single {
            it.description == "Fully qualified class names must be imported with use statements"
        }
        assertEquals(
            "\\Drupal\\Core\\Cache\\CacheBackendInterface",
            myFixture.file.text.substring(highlight.startOffset, highlight.endOffset)
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Import class and shorten reference"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Cache\CacheBackendInterface;

            final class Example {
              public function cache(): CacheBackendInterface {
                return \Drupal::cache();
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixImportsFullyQualifiedClassNamesInFile() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Cache\CacheBackendInterface;

            final class Example {
              public function build(\Drupal\Core\StringTranslation\TranslatableMarkup ${'$'}label): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

              public function cache(): \Drupal\Core\Cache\CacheBackendInterface {
                return \Drupal::cache();
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Cache\CacheBackendInterface;
            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\Core\Url;

            final class Example {
              public function build(TranslatableMarkup ${'$'}label): Url {
                return Url::fromRoute('example.route');
              }

              public function cache(): CacheBackendInterface {
                return \Drupal::cache();
              }

            }
            """.trimIndent()
        )
    }
}
