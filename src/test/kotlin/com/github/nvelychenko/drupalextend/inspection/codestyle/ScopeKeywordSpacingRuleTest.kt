package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScopeKeywordSpacingRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleScopeKeywordSpacingEnabled = true
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
        project.drupalExtendSettings.isCodeStylePropertyNameEnabled = false
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
        project.drupalExtendSettings.isCodeStyleElsePlacementEnabled = false
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = false
        project.drupalExtendSettings.isCodeStyleInlineCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = false
        project.drupalExtendSettings.isCodeStyleFileDocCommentEnabled = false
    }

    // -------------------------------------------------------------------------
    // Detection: double space after `public` before `function`
    // -------------------------------------------------------------------------

    fun testReportsDoubleSpaceAfterPublicBeforeFunction() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public  function foo(): void {}
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Scope keywords must be separated by a single space")
    }

    // -------------------------------------------------------------------------
    // Fix: `public  function` -> `public function`
    // -------------------------------------------------------------------------

    fun testQuickFixCollapsesDoubleSpaceAfterPublic() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public  function foo(): void {}
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize scope keyword spacing"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            class Example {
              public function foo(): void {}
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: `protected  static  $x` - two gaps both fixed in one pass
    // -------------------------------------------------------------------------

    fun testReportsDoubleSpaceInMultiKeywordFieldDeclaration() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              protected  static  ${'$'}x = 1;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Scope keywords must be separated by a single space")
    }

    fun testQuickFixCollapsesAllGapsInMultiKeywordFieldDeclaration() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              protected  static  ${'$'}x = 1;
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize scope keyword spacing"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            class Example {
              protected static ${'$'}x = 1;
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: tab after keyword
    // -------------------------------------------------------------------------

    fun testReportsTabAfterKeyword() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nclass Example {\n  public\tfunction foo(): void {}\n}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Scope keywords must be separated by a single space")
    }

    fun testQuickFixReplacesTabWithSingleSpace() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nclass Example {\n  public\tfunction foo(): void {}\n}\n"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize scope keyword spacing"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult("<?php\n\nclass Example {\n  public function foo(): void {}\n}\n")
    }

    // -------------------------------------------------------------------------
    // No false positive: correct single space is silent
    // -------------------------------------------------------------------------

    fun testNoFalsePositiveForCorrectSingleSpace() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public static function foo(): void {}

              protected readonly string ${'$'}bar = '';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Correct single-space declarations must not be flagged",
            descriptions.contains("Scope keywords must be separated by a single space")
        )
    }

    // -------------------------------------------------------------------------
    // Newline between keywords is silent (deliberate multi-line declaration)
    // -------------------------------------------------------------------------

    fun testKeywordFollowedByNewlineIsNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nclass Example {\n  public\n  function foo(): void {}\n}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "A keyword followed by a newline must not be flagged",
            descriptions.contains("Scope keywords must be separated by a single space")
        )
    }

    // -------------------------------------------------------------------------
    // Comment in gap: untouched
    // -------------------------------------------------------------------------

    fun testGapContainingCommentIsNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\nclass Example {\n  public /* inline */ function foo(): void {}\n}\n"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "A gap containing a comment must not be flagged",
            descriptions.contains("Scope keywords must be separated by a single space")
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the fix twice changes nothing
    // -------------------------------------------------------------------------

    fun testFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public  static  function foo(): void {}
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize scope keyword spacing"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — no further violations should exist.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Normalize scope keyword spacing"
        }
        assertTrue(
            "After applying the fix once, no further 'Normalize scope keyword spacing' actions should be available",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
