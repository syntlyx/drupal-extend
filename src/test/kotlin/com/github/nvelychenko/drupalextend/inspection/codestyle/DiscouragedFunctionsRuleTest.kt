package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DiscouragedFunctionsRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = true
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
    }

    // -------------------------------------------------------------------------
    // var_dump: flagged as standalone, fix removes the whole statement line
    // -------------------------------------------------------------------------

    fun testVarDumpStandaloneIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): void {
              var_dump(${'$'}x);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Debug function 'var_dump' should not be committed")
    }

    fun testRemoveFixDeletesStatementAndLeavesNoBlankLine() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): void {
              var_dump(${'$'}x);
              return ${'$'}x;
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Remove debug call" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_debug(${'$'}x): void {
              return ${'$'}x;
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // dpm: flagged even when unresolved (no devel module installed)
    // -------------------------------------------------------------------------

    fun testDpmUnresolvedIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}node): void {
              dpm(${'$'}node);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Debug function 'dpm' should not be committed")
    }

    // -------------------------------------------------------------------------
    // print_r: flagged without second arg, silent with TRUE second arg
    // -------------------------------------------------------------------------

    fun testPrintRWithoutSecondArgIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): void {
              print_r(${'$'}x);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Debug function 'print_r' should not be committed")
    }

    fun testPrintRWithTrueUppercaseIsNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): string {
              return print_r(${'$'}x, TRUE);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "print_r(\$x, TRUE) is a legitimate return-mode call and must not be flagged",
            descriptions.any { it.contains("print_r") }
        )
    }

    fun testPrintRWithTrueLowercaseInAssignmentIsNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): string {
              ${'$'}s = print_r(${'$'}x, true);
              return ${'$'}s;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "print_r(\$x, true) in assignment is a legitimate return-mode call and must not be flagged",
            descriptions.any { it.contains("print_r") }
        )
    }

    // -------------------------------------------------------------------------
    // Non-standalone: flagged but no remove fix offered
    // -------------------------------------------------------------------------

    fun testVarDumpInAssignmentIsFlaggedWithoutRemoveFix() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}y): void {
              ${'$'}x = var_dump(${'$'}y);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Debug function 'var_dump' should not be committed")

        val fixes = myFixture.getAllQuickFixes()
        assertFalse(
            "var_dump inside an assignment must not offer the remove fix",
            fixes.any { it.text == "Remove debug call" }
        )
    }

    // -------------------------------------------------------------------------
    // Method reference: $this->dump() must be silent
    // -------------------------------------------------------------------------

    fun testMethodCallIsNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {
              public function run(): void {
                ${'$'}this->dump();
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Method call \$this->dump() must not be flagged as discouraged",
            descriptions.any { it.contains("should not be committed") }
        )
    }

    // -------------------------------------------------------------------------
    // Case-insensitivity: VAR_DUMP must be flagged
    // -------------------------------------------------------------------------

    fun testVarDumpUppercaseIsFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): void {
              VAR_DUMP(${'$'}x);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "Debug function 'var_dump' should not be committed")
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the remove fix twice leaves the file unchanged
    // -------------------------------------------------------------------------

    fun testRemoveFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_debug(${'$'}x): void {
              var_dump(${'$'}x);
              return;
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first { it.text == "Remove debug call" }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — no more remove fix should be available.
        myFixture.doHighlighting()
        val secondFixes = myFixture.getAllQuickFixes().filter { it.text == "Remove debug call" }
        assertTrue(
            "After applying the fix once, no further 'Remove debug call' actions should be available",
            secondFixes.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
