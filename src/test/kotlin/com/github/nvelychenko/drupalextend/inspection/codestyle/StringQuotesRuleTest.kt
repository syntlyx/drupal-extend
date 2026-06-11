package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StringQuotesRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = true
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
    }

    // -------------------------------------------------------------------------
    // Detection: plain double-quoted string is flagged
    // -------------------------------------------------------------------------

    fun testPlainDoubleQuotedStringIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php
            ${'$'}x = "hello";
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Prefer single quotes for strings without interpolation or escapes"
        )
    }

    // -------------------------------------------------------------------------
    // Detection: single-quoted string is NOT reported
    // -------------------------------------------------------------------------

    fun testSingleQuotedStringIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php
            ${'$'}x = 'hello';
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Single-quoted strings must not be flagged",
            descriptions.contains("Prefer single quotes for strings without interpolation or escapes")
        )
    }

    // -------------------------------------------------------------------------
    // Detection: interpolated string with variable is NOT reported
    // -------------------------------------------------------------------------

    fun testInterpolatedVariableStringIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php
            ${'$'}name = 'world';
            ${'$'}x = "Hello ${'$'}name";
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Interpolated strings must not be flagged",
            descriptions.contains("Prefer single quotes for strings without interpolation or escapes")
        )
    }

    // -------------------------------------------------------------------------
    // Detection: string with \n (newline escape) is NOT reported
    // -------------------------------------------------------------------------

    fun testStringWithNewlineEscapeIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            // Use raw PHP: $x = "a\nb";
            "<?php\n\$x = \"a\\nb\";"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "String with \\n must not be flagged",
            descriptions.contains("Prefer single quotes for strings without interpolation or escapes")
        )
    }

    // -------------------------------------------------------------------------
    // Detection: string with \t (tab escape) is NOT reported
    // -------------------------------------------------------------------------

    fun testStringWithTabEscapeIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\$x = \"a\\tb\";"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "String with \\t must not be flagged",
            descriptions.contains("Prefer single quotes for strings without interpolation or escapes")
        )
    }

    // -------------------------------------------------------------------------
    // Detection: string with \" (escaped double-quote) IS reported — it IS
    // convertible (the escape becomes a literal " in single-quoted context)
    // -------------------------------------------------------------------------

    fun testStringWithEscapedDoubleQuoteIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            // PHP: $x = "say \"hi\"";
            "<?php\n\$x = \"say \\\"hi\\\"\";"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Prefer single quotes for strings without interpolation or escapes"
        )
    }

    // -------------------------------------------------------------------------
    // Fix: plain double-quoted string converts to single-quoted
    // -------------------------------------------------------------------------

    fun testQuickFixConvertsPlainDoubleQuotedToSingleQuoted() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php
            ${'$'}x = "hello";
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Convert to single-quoted string"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php
            ${'$'}x = 'hello';
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: string with \" converts correctly — backslash is removed, and the
    // result is 'say "hi"' (double-quote unescaped inside single-quoted string)
    // -------------------------------------------------------------------------

    fun testQuickFixConvertsEscapedDoubleQuoteCorrectly() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            // PHP source: $x = "say \"hi\"";
            "<?php\n\$x = \"say \\\"hi\\\"\";"
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Convert to single-quoted string"
        }
        myFixture.launchAction(intention)

        // Result should be: $x = 'say "hi"';
        myFixture.checkResult("<?php\n\$x = 'say \"hi\"';")
    }

    // -------------------------------------------------------------------------
    // String containing a single quote — double quotes are the RIGHT choice per
    // Drupal CS (converting would force escaping), so it is NOT reported.
    // e.g. "it's"  /  "UPDATE {t} SET uuid = '' WHERE …"
    // -------------------------------------------------------------------------

    fun testStringContainingSingleQuoteIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            // PHP source: $x = "it's"; $db->query("UPDATE {t} SET uuid = '' WHERE 1");
            "<?php\n\$x = \"it's\";\n\$db->query(\"UPDATE {t} SET uuid = '' WHERE 1\");"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Strings containing single quotes must not be reported",
            descriptions.contains("Prefer single quotes for strings without interpolation or escapes")
        )
    }

    // -------------------------------------------------------------------------
    // Fix: heredoc is NOT reported and no fix is offered
    // -------------------------------------------------------------------------

    fun testHeredocIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            "<?php\n\$x = <<<EOT\nhello world\nEOT;"
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Heredoc strings must not be flagged",
            descriptions.contains("Prefer single quotes for strings without interpolation or escapes")
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
            ${'$'}x = "hello";
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Convert to single-quoted string"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // Second application — no more violations should exist.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Convert to single-quoted string"
        }
        assertTrue(
            "After applying the fix once, no further 'Convert to single-quoted string' actions should be available",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }

    // -------------------------------------------------------------------------
    // Idempotency: single-quoted result produces no further violations
    // -------------------------------------------------------------------------

    fun testAlreadySingleQuotedDoesNotReport() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php
            ${'$'}x = 'hello';
            """.trimIndent()
        )

        val fixes = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it == "Prefer single quotes for strings without interpolation or escapes" }

        assertTrue(
            "Already-single-quoted string must not be reported after conversion",
            fixes.isEmpty()
        )
    }
}
