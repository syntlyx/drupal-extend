package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocBlockSummaryLengthRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = true
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
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = false
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
    // Detection: summary over 80 characters is flagged
    // -------------------------------------------------------------------------

    fun testSummaryOver80CharsIsReported() {
        // This summary line is 81 characters wide (including leading `" * "`).
        // The raw line for the summary is: " * " + 78 chars = 81 total.
        val longSummary = "A".repeat(78) // 78 'A' chars + " * " prefix = 81
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\n/**\n * $longSummary\n */\nfunction example(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertTrue(
            "Expected summary-length warning for a line of 81 characters",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: summary exactly 80 characters is not flagged
    // -------------------------------------------------------------------------

    fun testSummaryExactly80CharsIsNotReported() {
        // Raw line: " * " (3 chars) + 77 'A' chars = 80 total.
        val summary = "A".repeat(77)
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\n/**\n * $summary\n */\nfunction example(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "A summary line of exactly 80 characters must not be reported",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: summary under 80 characters is not flagged
    // -------------------------------------------------------------------------

    fun testShortSummaryIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Short summary.
             */
            function example(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "A short summary must not produce a length warning",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: summary containing a URL is not flagged
    // -------------------------------------------------------------------------

    fun testSummaryWithUrlIsNotReported() {
        // Build a line that would exceed 80 chars but contains a URL.
        val url = "https://www.example.com/some/very/long/path/that/makes/the/line/exceed/eighty/characters"
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\n/**\n * $url\n */\nfunction example(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "A summary containing a URL must not be reported even when over 80 characters",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: @param description lines over 80 chars are NOT flagged
    // -------------------------------------------------------------------------

    fun testLongParamDescriptionLineIsNotReported() {
        // The summary is short; the @param description line is over 80 chars.
        val longDescription = "X".repeat(90)
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\n/**\n * Short summary.\n *\n * @param string \$x\n *   $longDescription\n *\n * @return void\n */\nfunction example(string \$x): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Only the summary line is checked; description continuation lines must not be flagged",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: docblock with no summary must not report length
    // -------------------------------------------------------------------------

    fun testDocBlockWithNoSummaryDoesNotReportLength() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * @param string ${'$'}x
             *   The value.
             */
            function example(string ${'$'}x): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "A docblock without a summary must not produce a length warning",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-detection: rule disabled produces no report
    // -------------------------------------------------------------------------

    fun testDisabledRuleDoesNotReport() {
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
        val longSummary = "A".repeat(78)
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\n/**\n * $longSummary\n */\nfunction example(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Disabled rule must not produce any warning",
            descriptions.any { it.contains("must not exceed 80 characters") }
        )
    }

    fun testWrapFixMovesOverflowToContinuationLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Constructs the application generated document manager handling tradeline updates.
             */
            function example(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Wrap summary at 80 characters"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Constructs the application generated document manager handling tradeline
             * updates.
             */
            function example(): void {}
            """.trimIndent()
        )

        // Idempotency: every line is now under the limit — no further violation, no fix.
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(descriptions.any { it.contains("must not exceed 80 characters") })
    }

    fun testWrapFixHandlesVeryLongSummaryWithMultipleContinuations() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        val longSummary = (1..30).joinToString(" ") { "word$it" }
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * $longSummary.
             */
            function example(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Wrap summary at 80 characters"
        }
        myFixture.launchAction(intention)

        val overLimit = myFixture.file.text.lines().filter { it.length > 80 }
        assertEmpty("All wrapped lines must fit in 80 characters: $overLimit", overLimit)
    }

    fun testFileWideFixWrapsSummaryInOneRun() {
        // Regression: the umbrella docblock rewrite (triggered here by the missing summary
        // period) overlap-filtered the summary wrap, so the wrap only landed on a SECOND
        // action launch. The fixer now iterates to a fixpoint — one launch must suffice.
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = true
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        val longSummary = (1..14).joinToString(" ") { "word$it" }
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * $longSummary
             */
            function example(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        val overLimit = result.lines().filter { it.length > 80 }
        assertEmpty("Summary must be wrapped by a single file-wide fix run: $overLimit", overLimit)
        assertTrue("Summary words must be preserved, got:\n$result", result.contains("word14"))
    }
}
