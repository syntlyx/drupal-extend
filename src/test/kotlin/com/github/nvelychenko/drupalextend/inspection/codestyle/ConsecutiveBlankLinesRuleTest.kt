package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConsecutiveBlankLinesRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = true
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
        project.drupalExtendSettings.isCodeStylePropertyNameEnabled = false
        project.drupalExtendSettings.isCodeStyleScopeKeywordSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
    }

    // -------------------------------------------------------------------------
    // Detection: double blank line between functions is flagged
    // -------------------------------------------------------------------------

    fun testDoubleBlankLineBetweenFunctionsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\n\nfunction bar(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "No more than one consecutive blank line is allowed"
        )
    }

    // -------------------------------------------------------------------------
    // Detection: single blank line between functions is silent
    // -------------------------------------------------------------------------

    fun testSingleBlankLineBetweenFunctionsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\nfunction bar(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Single blank line must not be flagged",
            descriptions.any { it == "No more than one consecutive blank line is allowed" }
        )
    }

    // -------------------------------------------------------------------------
    // Detection: triple blank line produces exactly one report
    // -------------------------------------------------------------------------

    fun testTripleBlankLineReportsOneProblem() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\n\n\nfunction bar(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        val count = descriptions.count { it == "No more than one consecutive blank line is allowed" }

        assertEquals("Triple blank line should produce exactly one problem", 1, count)
    }

    // -------------------------------------------------------------------------
    // Detection: multiple separate violations each reported
    // -------------------------------------------------------------------------

    fun testMultipleSeparateViolationsEachReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\n\nfunction bar(): void {}\n\n\nfunction baz(): void {}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        val count = descriptions.count { it == "No more than one consecutive blank line is allowed" }

        assertEquals("Two separate double blank lines should produce two problems", 2, count)
    }

    // -------------------------------------------------------------------------
    // Fix: double blank line collapsed to one
    // -------------------------------------------------------------------------

    fun testQuickFixCollapsesDoubleBlankLineToOne() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\n\nfunction bar(): void {}\n"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Collapse consecutive blank lines"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult("<?php\n\nfunction foo(): void {}\n\nfunction bar(): void {}\n")
    }

    // -------------------------------------------------------------------------
    // Fix: triple blank line collapsed to one
    // -------------------------------------------------------------------------

    fun testQuickFixCollapsesTripleBlankLineToOne() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\n\n\nfunction bar(): void {}\n"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Collapse consecutive blank lines"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult("<?php\n\nfunction foo(): void {}\n\nfunction bar(): void {}\n")
    }

    // -------------------------------------------------------------------------
    // Fix: indentation after collapse preserved
    // -------------------------------------------------------------------------

    fun testQuickFixPreservesTrailingIndentation() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {\n\n\n  \$x = 1;\n}\n"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Collapse consecutive blank lines"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult("<?php\n\nfunction foo(): void {\n\n  \$x = 1;\n}\n")
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the fix twice changes nothing
    // -------------------------------------------------------------------------

    fun testFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nfunction foo(): void {}\n\n\nfunction bar(): void {}\n"
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Collapse consecutive blank lines"
        }
        myFixture.launchAction(firstIntention)
        val afterFirst = myFixture.file.text

        // Second pass — no collapse fix should be offered.
        myFixture.doHighlighting()
        val secondFixes = myFixture.getAllQuickFixes().filter {
            it.text == "Collapse consecutive blank lines"
        }
        assertTrue(
            "After collapsing, the fix must not be offered again",
            secondFixes.isEmpty()
        )

        assertEquals(afterFirst, myFixture.file.text)
    }

    // -------------------------------------------------------------------------
    // Blank lines inside a heredoc string are silent
    // -------------------------------------------------------------------------

    fun testBlankLinesInsideHeredocAreSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\n\$text = <<<EOT\nline one\n\n\nline two\nEOT;\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Blank lines inside a heredoc string must not be flagged",
            descriptions.any { it == "No more than one consecutive blank line is allowed" }
        )
    }
}
