package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ControlStructureSpacingRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = true
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
    }

    // -------------------------------------------------------------------------
    // Basic detection and fix
    // -------------------------------------------------------------------------

    fun testReportsMissingSpaceAfterIfKeyword() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if(${'$'}items) {}
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Control structures must use Drupal spacing")
    }

    fun testQuickFixNormalizesSpaceAfterIfKeyword() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if(${'$'}items) {}
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize control structure spacing"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) {}
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Bug regression: two control structures on one line
    // The fix for the SECOND (inner) construct must target that construct's own
    // keyword, not the outer one that appears earlier on the same line.
    // -------------------------------------------------------------------------

    fun testReportsForeachSpacingWhenIfIsAlsoOnTheSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        // The foreach is missing a space; the if is already correct.
        // The old (buggy) code would find `if` as the keyword for the foreach
        // element and produce a wrong/no-op edit.
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { foreach(${'$'}items as ${'$'}item) {} }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Control structures must use Drupal spacing")
    }

    fun testQuickFixTargetsForeachKeywordNotIfWhenBothOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { foreach(${'$'}items as ${'$'}item) {} }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize control structure spacing"
        }
        myFixture.launchAction(intention)

        // Only the `foreach` spacing should be corrected; the `if` was already fine.
        myFixture.checkResult(
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { foreach (${'$'}items as ${'$'}item) {} }
            }
            """.trimIndent()
        )
    }

    fun testNoFalsePositiveWhenIfAndForeachAreAlreadyCorrectOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { foreach (${'$'}items as ${'$'}item) {} }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Both constructs already have correct spacing; no violation should be reported",
            descriptions.contains("Control structures must use Drupal spacing")
        )
    }

    // -------------------------------------------------------------------------
    // Nested same-keyword: inner `if` must not be treated as the outer `if`
    // -------------------------------------------------------------------------

    fun testQuickFixTargetsInnerIfWhenNestedOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        // The outer `if` is correct; the inner `if` is missing a space.
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { if(${'$'}items) {} }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize control structure spacing"
        }
        myFixture.launchAction(intention)

        // Only the inner `if` should be fixed; outer was already correct.
        myFixture.checkResult(
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { if (${'$'}items) {} }
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the fix twice must not change anything further
    // -------------------------------------------------------------------------

    fun testFixIsIdempotentForForeachSpacing() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              foreach(${'$'}items as ${'$'}item) {}
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize control structure spacing"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — no more violations should exist for this rule.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Normalize control structure spacing"
        }
        assertTrue(
            "After applying the fix once, no further 'Normalize control structure spacing' actions should be available",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }

    fun testFixIsIdempotentWhenBothConstructsOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(array ${'$'}items): void {
              if (${'$'}items) { foreach(${'$'}items as ${'$'}item) {} }
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize control structure spacing"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — no more violations should exist for this rule.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Normalize control structure spacing"
        }
        assertTrue(
            "After applying the fix once, no further 'Normalize control structure spacing' actions should be available",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
