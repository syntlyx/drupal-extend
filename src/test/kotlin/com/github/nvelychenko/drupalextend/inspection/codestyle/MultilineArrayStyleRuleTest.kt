package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MultilineArrayStyleRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Core enabling
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleMultilineArrayStyleEnabled = true
        // Disable every other rule so highlights are unambiguous
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMemberVisibilityEnabled = false
        project.drupalExtendSettings.isCodeStyleConstantCasingEnabled = false
        project.drupalExtendSettings.isCodeStyleClosingPhpTagEnabled = false
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = false
        project.drupalExtendSettings.isCodeStyleShortArraySyntaxEnabled = false
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
    }

    // -------------------------------------------------------------------------
    // Bug 1: plain-value (positional) arrays are now inspected
    // -------------------------------------------------------------------------

    fun testReportsMissingTrailingCommaInPlainValueArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one',
                'two'
              ];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Multiline arrays must have a trailing comma after the last element"
        )
    }

    fun testQuickFixAddsTrailingCommaToPlainValueArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one',
                'two'
              ];
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

            function example(): array {
              return [
                'one',
                'two',
              ];
            }
            """.trimIndent()
        )
    }

    fun testReportsClosingBracketOnSameLineInPlainValueArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one',
                'two'];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Multiline arrays must have a trailing comma after the last element",
            "The closing bracket of a multiline array must be on a new line"
        )
    }

    fun testQuickFixNormalizesPlainValueArrayWithInlineClosingBracket() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one',
                'two'];
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

            function example(): array {
              return [
                'one',
                'two',
              ];
            }
            """.trimIndent()
        )
    }

    fun testValidPlainValueArrayDoesNotReport() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one',
                'two',
              ];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(descriptions.contains("Multiline arrays must have a trailing comma after the last element"))
        assertFalse(descriptions.contains("The closing bracket of a multiline array must be on a new line"))
    }

    fun testInlinePlainValueArrayDoesNotReport() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return ['one', 'two'];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(descriptions.contains("Multiline arrays must have a trailing comma after the last element"))
        assertFalse(descriptions.contains("The closing bracket of a multiline array must be on a new line"))
    }

    // -------------------------------------------------------------------------
    // Bug 2: comment-blind bracket matching is fixed
    // -------------------------------------------------------------------------

    fun testReportsMissingTrailingCommaWhenCommentContainsClosingBracket() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one', // ]
                'two'
              ];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Multiline arrays must have a trailing comma after the last element"
        )
    }

    fun testQuickFixCorrectlyNormalizesArrayWithCommentContainingBracket() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one', // ]
                'two'
              ];
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

            function example(): array {
              return [
                'one', // ]
                'two',
              ];
            }
            """.trimIndent()
        )
    }

    fun testReportsMissingTrailingCommaWhenStringContainsClosingBracket() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one]two',
                'three'
              ];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Multiline arrays must have a trailing comma after the last element"
        )
    }

    fun testQuickFixCorrectlyNormalizesArrayWithStringContainingBracket() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one]two',
                'three'
              ];
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

            function example(): array {
              return [
                'one]two',
                'three',
              ];
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the fix twice must not change anything further
    // -------------------------------------------------------------------------

    fun testFixIsIdempotentOnPlainValueArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one',
                'two'
              ];
            }
            """.trimIndent()
        )

        // First application
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline array style"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — must produce no more highlights for this rule
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Normalize multiline array style"
        }
        assertTrue(
            "After applying the fix once, no further 'Normalize multiline array style' actions should be available",
            secondIntentions.isEmpty()
        )

        // And the text must be stable
        assertEquals(afterFirstFix, myFixture.file.text)
    }

    fun testFixIsIdempotentOnHashArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one' => 1,
                'two' => 2
              ];
            }
            """.trimIndent()
        )

        // First application
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline array style"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — must produce no more highlights for this rule
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Normalize multiline array style"
        }
        assertTrue(
            "After applying the fix once, no further 'Normalize multiline array style' actions should be available",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }

    // -------------------------------------------------------------------------
    // Regression: existing hash array behaviour still works
    // -------------------------------------------------------------------------

    fun testReportsMissingTrailingCommaInHashArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one' => 1,
                'two' => 2
              ];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Multiline arrays must have a trailing comma after the last element"
        )
    }

    fun testQuickFixAddsTrailingCommaToHashArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example(): array {
              return [
                'one' => 1,
                'two' => 2
              ];
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

            function example(): array {
              return [
                'one' => 1,
                'two' => 2,
              ];
            }
            """.trimIndent()
        )
    }
}
