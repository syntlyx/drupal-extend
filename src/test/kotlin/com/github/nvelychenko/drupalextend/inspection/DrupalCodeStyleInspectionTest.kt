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
        project.drupalExtendSettings.isCodeStyleMemberVisibilityEnabled = true
        project.drupalExtendSettings.isCodeStyleConstantCasingEnabled = true
        project.drupalExtendSettings.isCodeStyleClosingPhpTagEnabled = true
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = false
        project.drupalExtendSettings.isCodeStyleShortArraySyntaxEnabled = true
        project.drupalExtendSettings.isCodeStyleMultilineArrayStyleEnabled = true
        project.drupalExtendSettings.isCodeStyleElseIfSyntaxEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = true
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = true
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleBlockBlankLinesEnabled = true
        project.drupalExtendSettings.isCodeStyleCastSpacingEnabled = true
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
        project.drupalExtendSettings.isCodeStyleDocBlockApiTagsEnabled = true
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

    fun testPropertyHooksAreNotTreatedAsFunctions() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "ValueTrait.php",
            """
            <?php

            /**
             * Provides a stored value.
             */
            trait ValueTrait {
              /**
               * Stored value.
               *
               * @var Value
               */
              protected Value ${'$'}value {
                set(Value ${'$'}value) {
                  ${'$'}this->value = ${'$'}value;
                }
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Method name should use camelCase"))
        assertFalse(descriptions.contains("Missing function doc comment"))
        assertFalse(descriptions.contains("Expected 1 blank line before function; 0 found"))
        assertFalse(descriptions.contains("Expected 1 blank line after function; 0 found"))
    }

    fun testReportsMissingMemberVisibilityProblems() {
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
              function build(): void {}

              static function helper(): void {}

              var ${'$'}legacy;

              const TYPE = 'example';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Class methods must declare visibility",
            "Class properties must declare visibility",
            "Class constants must declare visibility"
        )
    }

    fun testQuickFixAddsPublicMemberVisibilityInFile() {
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
              function build(): void {}

              static function helper(): void {}

              var ${'$'}legacy;

              const TYPE = 'example';

              final const VERSION = 1;
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
              public function build(): void {}

              public static function helper(): void {}

              public ${'$'}legacy;

              public const TYPE = 'example';

              final public const VERSION = 1;
            }
            """.trimIndent()
        )
    }

    fun testExplicitMemberVisibilityDoesNotReport() {
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
              public function build(): void {}

              protected static function helper(): void {}

              private ${'$'}legacy;

              public const TYPE = 'example';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Class methods must declare visibility"))
        assertFalse(descriptions.contains("Class properties must declare visibility"))
        assertFalse(descriptions.contains("Class constants must declare visibility"))
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

    fun testReportsFileWhitespaceProblems() {
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction example_build(): array {  \n  return [];\n}\n\n\n"
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Lines must not have trailing whitespace",
            "Files must end with exactly one newline"
        )
    }

    fun testQuickFixNormalizesFileWhitespace() {
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction example_build(): array {  \n  return [];\n}\n\n\n"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize file whitespace"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult("<?php\n\nfunction example_build(): array {\n  return [];\n}\n")
    }

    fun testQuickFixFixesFileWhitespaceInFile() {
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction example_build(): array {  \n  return [];\n}\n\n\n"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult("<?php\n\nfunction example_build(): array {\n  return [];\n}\n")
    }

    fun testValidFileWhitespaceDoesNotReport() {
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction example_build(): array {\n  return [];\n}\n"
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Lines must not have trailing whitespace"))
        assertFalse(descriptions.contains("Files must end with exactly one newline"))
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

    fun testReportsMultilineFunctionDeclarationStyleProblems() {
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
              public function build(string ${'$'}foo,
             string ${'$'}bar): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "The first parameter of a multiline function declaration must be on the line after the opening parenthesis",
            "Multiline function declaration parameters must be indented by two spaces",
            "Multiline function declarations must have a trailing comma after the last parameter",
            "The closing parenthesis of a multiline function declaration must be on a new line"
        )
    }

    fun testQuickFixNormalizesMultilineFunctionDeclaration() {
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
              public function build(string ${'$'}foo,
             string ${'$'}bar): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline function declaration"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(
                string ${'$'}foo,
                string ${'$'}bar,
              ): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesMultilineFunctionDeclarationsInFile() {
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
              public function build(string ${'$'}foo,
             string ${'$'}bar): array {
                return [];
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
              public function build(
                string ${'$'}foo,
                string ${'$'}bar,
              ): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testValidMultilineFunctionDeclarationDoesNotReport() {
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
              public function __construct(
                private readonly iterable ${'$'}actions,
                private readonly LockAction ${'$'}lockAction,
              ) {}

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("The first parameter of a multiline function declaration must be on the line after the opening parenthesis"))
        assertFalse(descriptions.contains("Multiline function declaration parameters must be indented by two spaces"))
        assertFalse(descriptions.contains("Multiline function declarations must have a trailing comma after the last parameter"))
        assertFalse(descriptions.contains("The closing parenthesis of a multiline function declaration must be on a new line"))
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

    fun testQuickFixAddsSummaryPunctuationBeforeClosingMarkerOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Builds the vehicle list<caret> */
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
             * Builds the vehicle list. */
            final class Example {}
            """.trimIndent()
        )
    }

    fun testFullDocBlockFixKeepsCustomTagsTogether() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Runs a worker<caret>
             *
             * @command lv:run
             * @aliases lv-run
             * @option group Group id.
             * @usage drush lv:run
             *   Run it.
             */
            final class Example {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal DocBlock"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue("Summary punctuation should be fixed", result.contains("Runs a worker."))
        assertFalse(
            "Umbrella fix must not insert blank lines between custom (Drush) tags",
            Regex("""@(command|aliases|option)\b.*\n\s*\*\s*\n\s*\*\s*@""").containsMatchIn(result)
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

    fun testReportsBlockBlankLineProblems() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): array {

              if (${'$'}items) {

                return ${'$'}items;

              }

              return [];

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Block opening braces must not be followed by a blank line",
            "Block closing braces must not be preceded by a blank line"
        )
    }

    fun testQuickFixNormalizesBlockBlankLines() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(): array {

              return [];

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize block blank lines"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(): array {
              return [];
            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesBlockBlankLinesInFile() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): array {

              if (${'$'}items) {

                return ${'$'}items;

              }

              return [];

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

            function example_build(array ${'$'}items): array {
              if (${'$'}items) {
                return ${'$'}items;
              }

              return [];
            }
            """.trimIndent()
        )
    }

    fun testValidBlockBlankLinesDoNotReport() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): array {
              if (${'$'}items) {
                return ${'$'}items;
              }

              return [];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Block opening braces must not be followed by a blank line"))
        assertFalse(descriptions.contains("Block closing braces must not be preceded by a blank line"))
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
               * @param \Drupal\my_module\Application\Application ${'$'}application
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

    fun testFileDocBlockDescriptionCountsAsSummary() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            /**
             * @file
             * My module implementation.
             */
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("DocBlock is missing a short summary"))
    }

    fun testReportsMissingFileDocBlockSummary() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            /**
             * @file
             */
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

    fun testDocBlockDescriptionIndentationAcceptsDrupalBulletList() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            /**
             * Controls access to a node.
             *
             * @param \Drupal\node\NodeInterface ${'$'}node
             *   Either a node entity or the machine name of the content type on which to
             *   perform the access check.
             * @param string ${'$'}op
             *   The operation to be performed. Possible values:
             *   - "create"
             *   - "delete"
             *   - "update"
             *   - "view".
             * @param \Drupal\Core\Session\AccountInterface ${'$'}account
             *   The user object to perform the access check operation on.
             *
             * @return \Drupal\Core\Access\AccessResultInterface
             *   The access result.
             */
            function nll_core_node_access(${'$'}node, string ${'$'}op, ${'$'}account) {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@param description must be indented by two spaces"))
    }

    fun testDocBlockDescriptionIndentationAcceptsWrappedDrupalBulletItems() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            interface SelectInterface {}

            final class Example {
              /**
               * Applies a set of filters to a query object.
               *
               * @param SelectInterface ${'$'}query
               *   The query object to which the filters will be applied.
               * @param array ${'$'}filters
               *   An associative array of filters where each key represents a column, and
               *   the value is an array containing the filter specifications. Each filter
               *   specification can include the following keys:
               *   - type: (string) The type of filter (e.g., 'string', 'select', 'boolean',
               *     'numeric_range', 'date_range'). Defaults to 'string'.
               *   - value: (mixed) The value or values to filter by. The expected format
               *     varies based on the filter type.
               */
              public function applyFilters(SelectInterface ${'$'}query, array ${'$'}filters): void {}
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@param description must be indented by two spaces"))
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

    fun testMultilineArrayShapeParamTypeWithNameOnClosingLineDoesNotReportMissingNameOrDescription() {
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
               * Generates an audit report for the given application data.
               *
               * This method processes the application information, retrieves related logs,
               * notifications, and other relevant data, and generates a PDF report. The
               * generated report is returned as a decoded binary string.
               *
               * @param array{
               *   application: \Drupal\nll_core\Application\Application,
               *   manager: \Drupal\nll_core\Application\ApplicationManager,
               *   utilities: \Drupal\nll_core\Utilities\UtilitiesManager,
               *   renderer: \Drupal\Core\Render\RendererInterface,
               *   fs: \Drupal\Core\File\FileSystemInterface,
               *   mailLogRepository: \Drupal\nll_core\MailManager\MailLogRepository,
               *   db: \Drupal\nll_core\MailManager\Archive\MailLogArchiveInterface,
               *   pathResolver: \Drupal\Core\Extension\ExtensionPathResolver
               * } ${'$'}info
               *
               * @return string|null
               *   The generated audit report as a decoded binary string.
               */
              public function generateAuditReport(array ${'$'}info): ?string {
                return NULL;
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@param tag is missing parameter name '${'$'}info'"))
        assertFalse(descriptions.contains("@param tag is missing description"))
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
               * @param \Drupal\my_module\OAuth\Client\ClientContext ${'$'}client
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

    fun testReportsDocBlockApiTagSyntaxProblems() {
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
               * @throws RuntimeException Something failed
               * @throws InvalidArgumentException
               *
               * @see \Drupal\example\Example::build() for extra details
               */
              public function build(): array {
                return [];
              }

              /**
               * Old build helper.
               *
               * @deprecated Use build() instead.
               */
              public function oldBuild(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "@throws description must be on a continuation line",
            "@throws description should end with punctuation",
            "@throws exception class must use a fully qualified namespace",
            "@see tag must contain only one reference",
            "@deprecated tag must use Drupal deprecation text format",
            "@deprecated tag must be followed by @see"
        )
    }

    fun testQuickFixNormalizesThrowsDocBlockTag() {
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
               * @throws RuntimeException Something failed
               */
              public function build(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Fully qualify @throws exception class"
        })
        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Move @throws description to continuation line"
        })
        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().single {
            it.text == "Add @throws description punctuation"
        })

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Builds rows.
               *
               * @throws \RuntimeException
               *   Something failed.
               */
              public function build(): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixFullyQualifiesImportedThrowsException() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.addFileToProject(
            "Experian/Exceptions/ExperianCreditReportReferenceIntegrityException.php",
            """
            <?php

            namespace Drupal\nll_core\Experian\Exceptions;

            class ExperianCreditReportReferenceIntegrityException extends \RuntimeException {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "ExperianService.php",
            """
            <?php

            namespace Drupal\nll_core\Experian;

            use Drupal\nll_core\Experian\Exceptions\ExperianCreditReportReferenceIntegrityException;

            final class ExperianService {
              /**
               * Builds rows.
               *
               * @throws ExperianCreditReportReferenceIntegrityException
               *   Something failed.
               */
              public function build(): void {
                throw new ExperianCreditReportReferenceIntegrityException();
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().first {
            it.text == "Fully qualify @throws exception class"
        })

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\nll_core\Experian;

            use Drupal\nll_core\Experian\Exceptions\ExperianCreditReportReferenceIntegrityException;

            final class ExperianService {
              /**
               * Builds rows.
               *
               * @throws \Drupal\nll_core\Experian\Exceptions\ExperianCreditReportReferenceIntegrityException
               *   Something failed.
               */
              public function build(): void {
                throw new ExperianCreditReportReferenceIntegrityException();
              }

            }
            """.trimIndent()
        )
    }

    fun testFullDocBlockFixFullyQualifiesImportedThrowsException() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.addFileToProject(
            "Experian/Exceptions/ExperianCreditReportReferenceIntegrityException.php",
            """
            <?php

            namespace Drupal\nll_core\Experian\Exceptions;

            class ExperianCreditReportReferenceIntegrityException extends \RuntimeException {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "ExperianService.php",
            """
            <?php

            namespace Drupal\nll_core\Experian;

            use Drupal\nll_core\Experian\Exceptions\ExperianCreditReportReferenceIntegrityException;

            final class ExperianService {
              /**
               * Builds rows
               *
               * @throws ExperianCreditReportReferenceIntegrityException Something failed
               */
              public function build(): void {
                throw new ExperianCreditReportReferenceIntegrityException();
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal DocBlock"
        })

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\nll_core\Experian;

            use Drupal\nll_core\Experian\Exceptions\ExperianCreditReportReferenceIntegrityException;

            final class ExperianService {
              /**
               * Builds rows.
               *
               * @throws \Drupal\nll_core\Experian\Exceptions\ExperianCreditReportReferenceIntegrityException
               *   Something failed.
               */
              public function build(): void {
                throw new ExperianCreditReportReferenceIntegrityException();
              }

            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesFullDocBlockWithThrowsDescription() {
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDocMissingTagsEnabled = true
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Builds rows
               *
               * @throws RuntimeException Something failed
               */
              public function build(): array {
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
               * @return array
               *   The array.
               *
               * @throws \RuntimeException
               *   Something failed.
               */
              public function build(): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    fun testValidDeprecatedDocBlockTagsDoNotReport() {
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
               * Old build helper.
               *
               * @deprecated in example:1.2.3 and is removed from example:2.0.0. Use build() instead.
               * @see https://www.drupal.org/node/123456
               */
              public function oldBuild(): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("@deprecated tag must use Drupal deprecation text format"))
        assertFalse(descriptions.contains("@deprecated tag must be followed by @see"))
        assertFalse(descriptions.contains("@see tag must contain only one reference"))
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

    fun testReportsBlankLineBetweenDocBlockAndDeclaration() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Returns stored value.
               *
               * @return Value
               *   Stored value.
               */

              protected function value(): Value {
                return ${'$'}this->value;
              }

              /**
               * Stored value.
               *
               * @var Value
               */

              protected Value ${'$'}value;

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }
        val message = "DocBlock must be immediately followed by attributes or the documented declaration"

        assertEquals(2, descriptions.count { it == message })
    }

    fun testAllowsDocBlockImmediatelyFollowedByAttribute() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Returns stored value.
               *
               * @return Value
               *   Stored value.
               */
              #[ExampleAttribute]
              protected function value(): Value {
                return ${'$'}this->value;
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(
            descriptions.contains("DocBlock must be immediately followed by attributes or the documented declaration")
        )
    }

    fun testQuickFixRemovesBlankLineBetweenDocBlockAndDeclaration() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              /**
               * Returns stored value.
               *
               * @return Value
               *   Stored value.
               */

              protected function value(): Value {
                return ${'$'}this->value;
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Remove blank line after DocBlock"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              /**
               * Returns stored value.
               *
               * @return Value
               *   Stored value.
               */
              protected function value(): Value {
                return ${'$'}this->value;
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
        project.drupalExtendSettings.isCodeStyleFunctionDocMissingTagsEnabled = true
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
               *
               * @return string
               *   The string.
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

    fun testReportsMultilineArrayStyleProblems() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(): array {
              return [
                'one' => 1,
                'two' => 2];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Multiline arrays must have a trailing comma after the last element",
            "The closing bracket of a multiline array must be on a new line"
        )
    }

    fun testQuickFixNormalizesMultilineArrayStyle() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(): array {
              return [
                'one' => 1,
                'two' => 2];
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline array style"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(): array {
              return [
                'one' => 1,
                'two' => 2,
              ];
            }
            """.trimIndent()
        )
    }

    fun testQuickFixFixesMultilineArrayStyleInFile() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(): array {
              return [
                'items' => [
                  'one' => 1
                ]
              ];
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

            function example_build(): array {
              return [
                'items' => [
                  'one' => 1,
                ],
              ];
            }
            """.trimIndent()
        )
    }

    fun testValidMultilineArrayStyleDoesNotReport() {
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(): array {
              ${'$'}inline = ['one', 'two'];
              return [
                'one' => 1,
                'two' => 2,
              ];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Multiline arrays must have a trailing comma after the last element"))
        assertFalse(descriptions.contains("The closing bracket of a multiline array must be on a new line"))
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

    fun testQuickFixNormalizesCastSpacing() {
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
              public function build(mixed ${'$'}id): int {
                ${'$'}one = (int)${'$'}id;
                ${'$'}two = (bool)  ${'$'}id;
                ${'$'}three = (string)	${'$'}id;
                return (int) ${'$'}one;
              }

            }
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val descriptions = highlights.mapNotNull { it.description }
        assertContainsElements(descriptions, "Casts must be followed by a single space")
        val highlightedTexts = highlights.filter {
            it.description == "Casts must be followed by a single space"
        }.map {
            myFixture.file.text.substring(it.startOffset, it.endOffset)
        }
        assertContainsElements(highlightedTexts, "(int)", "(bool)", "(string)")

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(mixed ${'$'}id): int {
                ${'$'}one = (int) ${'$'}id;
                ${'$'}two = (bool) ${'$'}id;
                ${'$'}three = (string) ${'$'}id;
                return (int) ${'$'}one;
              }

            }
            """.trimIndent()
        )
    }

    fun testValidCastSpacingDoesNotReport() {
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
              public function build(mixed ${'$'}id): int {
                return (int) ${'$'}id;
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Casts must be followed by a single space"))
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

    fun testDrushAnnotationTagsDoNotTriggerTagGroupSpacingWarning() {
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
               * List vault loans.
               *
               * @command lv:loans
               * @aliases lv-loans
               * @option status Filter by loan status.
               * @option limit Maximum number of results.
               * @usage lv:loans --status=active
               *   List all active loans.
               */
              public function loans(): void {}

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("DocBlock tag groups must be separated by a blank line"))
    }

    fun testParamImmediatelyFollowedByReturnStillReportsTagGroupSpacingWarning() {
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
               * Finds a loan by ID.
               *
               * @param string ${'$'}id
               *   The loan ID.
               * @return array
               *   The loan data.
               */
              public function find(string ${'$'}id): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "DocBlock tag groups must be separated by a blank line")
    }

    fun testIncompleteDeclarationsDoNotTriggerNamingRulesOrCrash() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        // Half-typed declarations with no name yet are normal in the editor; all rules must
        // survive the broken PSI and the naming rules must stay silent.
        myFixture.configureByText(
            "incomplete.php",
            """
            <?php

            class

            function
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertDoesntContain(descriptions, "Class name should use PascalCase")
        assertDoesntContain(descriptions, "Function name should use snake_case")
    }
}
