package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocBlockShortTypesRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = true
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
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
        project.drupalExtendSettings.isCodeStyleElsePlacementEnabled = false
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = false
        project.drupalExtendSettings.isCodeStyleInlineCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = false
        project.drupalExtendSettings.isCodeStylePropertyNameEnabled = false
        project.drupalExtendSettings.isCodeStyleScopeKeywordSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleFileDocCommentEnabled = false
    }

    // -------------------------------------------------------------------------
    // Detection: @param integer is flagged
    // -------------------------------------------------------------------------

    fun testParamIntegerIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param integer ${'$'}x
             *   The value.
             *
             * @return string
             *   Result.
             */
            function example(int ${'$'}x): string {
              return '';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertTrue(
            "Expected short-types warning for 'integer'",
            descriptions.any { it.contains("integer") && it.contains("int") }
        )
    }

    // -------------------------------------------------------------------------
    // Detection: @return boolean is flagged
    // -------------------------------------------------------------------------

    fun testReturnBooleanIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @return boolean
             *   TRUE or FALSE.
             */
            function example(): bool {
              return TRUE;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertTrue(
            "Expected short-types warning for 'boolean'",
            descriptions.any { it.contains("boolean") && it.contains("bool") }
        )
    }

    // -------------------------------------------------------------------------
    // Detection: compound type integer[] is flagged
    // -------------------------------------------------------------------------

    fun testParamArrayOfIntegerIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param integer[] ${'$'}items
             *   Items.
             *
             * @return void
             */
            function example(array ${'$'}items): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertTrue(
            "Expected short-types warning for 'integer[]'",
            descriptions.any { it.contains("integer") }
        )
    }

    // -------------------------------------------------------------------------
    // Detection: union type integer|null is flagged
    // -------------------------------------------------------------------------

    fun testParamUnionTypeIntegerNullIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param integer|null ${'$'}x
             *   The value or null.
             *
             * @return void
             */
            function example(?int ${'$'}x): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertTrue(
            "Expected short-types warning for 'integer|null'",
            descriptions.any { it.contains("integer") }
        )
    }

    // -------------------------------------------------------------------------
    // Detection: case-insensitive — Integer (capital I) is also flagged.
    // Assumption: DocBlock type tokens are case-insensitive scalars; a capital
    // `Integer` in a type position is still a scalar alias, not a class name.
    // -------------------------------------------------------------------------

    fun testParamIntegerCaseInsensitiveIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param Integer ${'$'}x
             *   The value.
             *
             * @return void
             */
            function example(int ${'$'}x): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertTrue(
            "Expected short-types warning for 'Integer' (case-insensitive match)",
            descriptions.any { it.lowercase().contains("integer") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: word `Integration` must NOT be flagged (word boundary)
    // -------------------------------------------------------------------------

    fun testIntegrationTypeIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Integration {}

            /**
             * Example function.
             *
             * @param Integration ${'$'}integration
             *   The integration.
             *
             * @return void
             */
            function example(Integration ${'$'}integration): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Must not flag 'Integration' — word boundary must prevent partial match",
            descriptions.any { it.lowercase().contains("integer") || it.lowercase().contains("integration") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: description text containing "integer" must not be flagged
    // -------------------------------------------------------------------------

    fun testDescriptionTextWithIntegerWordIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Returns an integer value from the cache.
             *
             * @param string ${'$'}key
             *   The cache key.
             *
             * @return int
             *   The stored integer value.
             */
            function example(string ${'$'}key): int {
              return 0;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Must not flag the word 'integer' appearing in the summary or description text",
            descriptions.any { it.contains("Use short scalar type") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: already-short types must not be flagged
    // -------------------------------------------------------------------------

    fun testShortTypesAreNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param int ${'$'}a
             *   First.
             * @param bool ${'$'}b
             *   Second.
             * @param float ${'$'}c
             *   Third.
             *
             * @return void
             */
            function example(int ${'$'}a, bool ${'$'}b, float ${'$'}c): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Short scalar types must not produce any warning",
            descriptions.any { it.contains("Use short scalar type") }
        )
    }

    // -------------------------------------------------------------------------
    // Fix: @param integer $x -> @param int $x
    // -------------------------------------------------------------------------

    fun testQuickFixReplacesIntegerWithInt() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param integer ${'$'}x
             *   The value.
             *
             * @return void
             */
            function example(int ${'$'}x): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().firstOrNull {
            it.text == "Use short scalar type name"
        } ?: error("Quick fix 'Use short scalar type name' not found")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            <?php

            /**
             * Example function.
             *
             * @param int ${'$'}x
             *   The value.
             *
             * @return void
             */
            function example(int ${'$'}x): void {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: @return boolean -> @return bool
    // -------------------------------------------------------------------------

    fun testQuickFixReplacesBooleanWithBool() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @return boolean
             *   TRUE or FALSE.
             */
            function example(): bool {
              return TRUE;
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().firstOrNull {
            it.text == "Use short scalar type name"
        } ?: error("Quick fix 'Use short scalar type name' not found")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            <?php

            /**
             * Example function.
             *
             * @return bool
             *   TRUE or FALSE.
             */
            function example(): bool {
              return TRUE;
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: integer[] -> int[]
    // -------------------------------------------------------------------------

    fun testQuickFixReplacesIntegerArrayWithIntArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param integer[] ${'$'}items
             *   Items.
             *
             * @return void
             */
            function example(array ${'$'}items): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().firstOrNull {
            it.text == "Use short scalar type name"
        } ?: error("Quick fix 'Use short scalar type name' not found")
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
            <?php

            /**
             * Example function.
             *
             * @param int[] ${'$'}items
             *   Items.
             *
             * @return void
             */
            function example(array ${'$'}items): void {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the fix twice must change nothing
    // -------------------------------------------------------------------------

    fun testFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example function.
             *
             * @param integer ${'$'}x
             *   Value.
             *
             * @return boolean
             *   Result.
             */
            function example(int ${'$'}x): bool {
              return TRUE;
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstFix = myFixture.getAllQuickFixes().firstOrNull {
            it.text == "Use short scalar type name"
        } ?: error("Quick fix not found on first application")
        myFixture.launchAction(firstFix)

        val afterFirstFix = myFixture.file.text

        // Second application — must produce no more highlights for this rule.
        myFixture.doHighlighting()
        val remainingFixes = myFixture.getAllQuickFixes().filter {
            it.text == "Use short scalar type name"
        }
        assertTrue(
            "After applying the fix once, no further 'Use short scalar type name' actions should be available",
            remainingFixes.isEmpty()
        )

        // Text must be stable.
        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
