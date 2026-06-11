package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ElsePlacementRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleElsePlacementEnabled = true
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
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = false
        project.drupalExtendSettings.isCodeStyleInlineCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = false
    }

    // -------------------------------------------------------------------------
    // Detection: } else { on one line must be flagged
    // -------------------------------------------------------------------------

    fun testReportsElseOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              } else {
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "else/elseif must start on a new line")
    }

    // -------------------------------------------------------------------------
    // Detection: } else if (...) { on one line must be flagged
    // (elseif as one keyword is a ControlStatement, not Else; only `else if`
    //  two-word form creates an Else PSI element dispatched to this rule)
    // -------------------------------------------------------------------------

    fun testReportsElseIfTwoWordsOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              } else if (${'$'}b) {
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "else/elseif must start on a new line")
    }

    // -------------------------------------------------------------------------
    // No false positive: elseif one-keyword form is a ControlStatement, not Else
    // — the placement rule (registered only for Else target) must not crash on it
    // -------------------------------------------------------------------------

    fun testReportsAndFixesElseIfOneKeywordOnSameLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              } elseif (${'$'}b) {
              }
            }
            """.trimIndent()
        )

        // The one-keyword elseif is an ElseIf PSI element, routed through the
        // CONTROL_STATEMENT target — the most common Drupal violation shape.
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "else/elseif must start on a new line")

        val intention = myFixture.getAllQuickFixes().single { it.text == "Move else/elseif to a new line" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              }
              elseif (${'$'}b) {
              }
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // No false positive: else already on a new line
    // -------------------------------------------------------------------------

    fun testNoFalsePositiveWhenElseAlreadyOnNewLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              }
              else {
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "else already on new line — no violation expected",
            descriptions.contains("else/elseif must start on a new line")
        )
    }

    // -------------------------------------------------------------------------
    // No false positive: comment between } and else — must not be flagged
    // -------------------------------------------------------------------------

    fun testNoFlagWhenCommentBetweenBraceAndElse() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              } // comment
              else {
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "comment between } and else — must not be flagged",
            descriptions.contains("else/elseif must start on a new line")
        )
    }

    // -------------------------------------------------------------------------
    // Quick fix: } else { → } + newline + indent + else {
    // -------------------------------------------------------------------------

    fun testQuickFixMovesElseToNewLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              } else {
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Move else/elseif to a new line"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              }
              else {
              }
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Quick fix: } else if (...) { → newline + indent (two-word form)
    // -------------------------------------------------------------------------

    fun testQuickFixMovesElseIfTwoWordsToNewLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              } else if (${'$'}b) {
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Move else/elseif to a new line"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example(): void {
              if (${'$'}a) {
              }
              else if (${'$'}b) {
              }
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Nested if: indent must match the nested if, not the outer one
    // -------------------------------------------------------------------------

    fun testQuickFixUsesCorrectIndentForNestedIf() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): void {
              if (${'$'}outer) {
                if (${'$'}inner) {
                } else {
                }
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Move else/elseif to a new line"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example(): void {
              if (${'$'}outer) {
                if (${'$'}inner) {
                }
                else {
                }
              }
            }
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

            function example(): void {
              if (${'$'}a) {
              } else {
              }
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Move else/elseif to a new line"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — no more violations for this rule should exist.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Move else/elseif to a new line"
        }
        assertTrue(
            "After applying the fix once no further 'Move else/elseif to a new line' actions should be available",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
