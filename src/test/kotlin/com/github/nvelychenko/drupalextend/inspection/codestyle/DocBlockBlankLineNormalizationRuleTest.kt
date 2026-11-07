package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Covers the docblock blank-line normalization rule and, crucially, the umbrella whole-file
 * rewrite it feeds into. The regression these lock in: the umbrella fixer used to strip the
 * blank line that separates a one-line summary from its longer description (a valid, and
 * expected, Drupal docblock shape), because it dropped every blank body line and only ever
 * re-inserted separators around tag groups.
 */
class DocBlockBlankLineNormalizationRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true

        // The umbrella rewrite derives blank-line separators from this trio, so keep them on.
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = true
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = true

        // Disable everything else so the whole-file fixer makes no unrelated edits.
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
        project.drupalExtendSettings.isCodeStyleFunctionDocMissingTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleBlockBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleCastSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleUseStatementSortingEnabled = false
        project.drupalExtendSettings.isCodeStyleFullyQualifiedClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockDeclarationSpacingEnabled = false
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
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = false
    }

    private fun applyWholeFileFix(): Boolean {
        return WriteCommandAction.runWriteCommandAction<Boolean>(project) {
            DrupalCodeStyleFileFixer.apply(project, myFixture.file)
        }
    }

    // -------------------------------------------------------------------------
    // Regression: the summary ↔ description blank line survives the umbrella fix.
    // -------------------------------------------------------------------------

    fun testWholeFileFixKeepsSummaryDescriptionBlankLine() {
        val source = """
            <?php

            /**
             * Manager for client-specific strategies.
             *
             * This manager is responsible for selecting the appropriate client strategy
             * based on the application.
             */
            class ClientStrategyManager {}
            """.trimIndent()
        myFixture.configureByText("Example.php", source)

        val changed = applyWholeFileFix()

        assertFalse("A valid summary/description docblock must not be rewritten", changed)
        myFixture.checkResult(source)
    }

    // -------------------------------------------------------------------------
    // Multi-paragraph descriptions keep every paragraph-separating blank line.
    // -------------------------------------------------------------------------

    fun testWholeFileFixKeepsMultiParagraphDescriptionBlankLines() {
        val source = """
            <?php

            /**
             * Short summary.
             *
             * First description paragraph that explains the intent.
             *
             * Second description paragraph with more detail.
             */
            class Example {}
            """.trimIndent()
        myFixture.configureByText("Example.php", source)

        val changed = applyWholeFileFix()

        assertFalse("Multi-paragraph descriptions must keep their blank lines", changed)
        myFixture.checkResult(source)
    }

    // -------------------------------------------------------------------------
    // Description-area blank lines coexist with the tag-group separators.
    // -------------------------------------------------------------------------

    fun testWholeFileFixKeepsDescriptionBlankAlongsideTags() {
        val source = """
            <?php

            /**
             * Does something useful.
             *
             * Longer description spanning
             * two lines.
             *
             * @param string ${'$'}value
             *   The value.
             */
            function example_do(string ${'$'}value) {}
            """.trimIndent()
        myFixture.configureByText("Example.php", source)

        val changed = applyWholeFileFix()

        assertFalse("Description blank line must survive next to tag separators", changed)
        myFixture.checkResult(source)
    }

    // -------------------------------------------------------------------------
    // Redundant consecutive blank lines in the description are still collapsed.
    // -------------------------------------------------------------------------

    fun testWholeFileFixCollapsesConsecutiveDescriptionBlankLines() {
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Short summary.
             *
             *
             * Description after a doubled blank line.
             */
            class Example {}
            """.trimIndent()
        )

        val changed = applyWholeFileFix()

        assertTrue("Doubled blank lines should be collapsed", changed)
        myFixture.checkResult(
            """
            <?php

            /**
             * Short summary.
             *
             * Description after a doubled blank line.
             */
            class Example {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // A trailing blank line just before the closing marker is still removed.
    // -------------------------------------------------------------------------

    fun testWholeFileFixRemovesTrailingBlankLineBeforeClose() {
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Short summary.
             *
             * Description.
             *
             */
            class Example {}
            """.trimIndent()
        )

        val changed = applyWholeFileFix()

        assertTrue("A blank line before the closing marker should be removed", changed)
        myFixture.checkResult(
            """
            <?php

            /**
             * Short summary.
             *
             * Description.
             */
            class Example {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // The standalone inspection does not flag the valid summary/description shape.
    // -------------------------------------------------------------------------

    fun testValidSummaryDescriptionDocBlockNotFlagged() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Manager for client-specific strategies.
             *
             * This manager selects the appropriate client strategy.
             */
            class ClientStrategyManager {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "The blank line between summary and description must not be flagged as redundant",
            descriptions.any { it == "DocBlock contains a redundant blank line" }
        )
    }
}
