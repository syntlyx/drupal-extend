package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FileDocCommentRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleFileDocCommentEnabled = true
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
    // Missing docblock entirely
    // -------------------------------------------------------------------------

    fun testReportsMissingFileDocBlockInModuleFile() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            function my_module_hook(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Procedural Drupal files must start with a @file docblock"
        )
    }

    fun testReportsMissingFileDocBlockInInstallFile() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.install",
            """
            <?php

            function my_module_install(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Procedural Drupal files must start with a @file docblock"
        )
    }

    // The .install extension is also registered as PHP by the plugin, which allows
    // asserting a second distinct extension without relying on platform-side associations
    // for .theme / .inc / .profile.

    // -------------------------------------------------------------------------
    // Proper @file docblock — no violation
    // -------------------------------------------------------------------------

    fun testSilentWhenModuleFileHasProperFileDocBlock() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            /**
             * @file
             * My module description.
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Expected no file doc comment violation when @file is present",
            descriptions.contains("Procedural Drupal files must start with a @file docblock")
        )
        assertFalse(
            "Expected no @file tag violation when @file is present",
            descriptions.contains("File docblock is missing the @file tag")
        )
    }

    // -------------------------------------------------------------------------
    // Regular .php file — rule must not fire
    // -------------------------------------------------------------------------

    fun testSilentForRegularPhpFile() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "MyClass.php",
            """
            <?php

            function standalone_function(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            "Rule must not fire on regular .php files",
            descriptions.contains("Procedural Drupal files must start with a @file docblock")
        )
    }

    // -------------------------------------------------------------------------
    // Fix: insert full @file docblock when none exists
    // -------------------------------------------------------------------------

    fun testQuickFixInsertsFileDocBlockAfterOpenTag() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            function my_module_hook(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Insert @file docblock"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php
            /**
             * @file
             * Hook implementations for the My Module module.
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )
    }

    fun testFileWideFixDoesNotCorruptDocBlockNeedingBothFileTagAndNormalization() {
        // Regression: the @file-tag insertion and the umbrella docblock rewrite both target the
        // same top docblock; overlapping edits used to corrupt it. The fixer now runs internal
        // passes to a fixpoint, so a single action launch must converge without broken output.
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = true
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php
            /**
             * Some description without punctuation
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        myFixture.getAllQuickFixes()
            .firstOrNull { it.text == "Fix Drupal code style in file" }
            ?.let { myFixture.launchAction(it) }

        val result = myFixture.file.text
        assertTrue("Docblock must contain the @file tag, got:\n$result", result.contains("* @file"))
        assertTrue(
            "Summary text must be preserved intact, got:\n$result",
            result.contains("* Some description without punctuation")
        )
        assertEquals("Docblock must stay well-formed", 1, Regex("""/\*\*""").findAll(result).count())
        assertEquals(1, Regex("""\*/""").findAll(result).count())
    }

    fun testFileWideFixGeneratesFileDocBlockWithConventionDescription() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "nll_core.install",
            """
            <?php

            function nll_core_install(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(
            "File-wide fix must generate the @file docblock, got:\n$result",
            result.contains("/**\n * @file\n * Install, update and uninstall functions for the Nll Core module.\n */")
        )
    }

    // -------------------------------------------------------------------------
    // Docblock without @file tag
    // -------------------------------------------------------------------------

    fun testReportsDocBlockWithoutFileTag() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            /**
             * My module description.
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(descriptions, "File docblock is missing the @file tag")
        assertFalse(
            "Missing docblock violation should not fire when a docblock exists",
            descriptions.contains("Procedural Drupal files must start with a @file docblock")
        )
    }

    fun testQuickFixInsertsFileTagIntoExistingDocBlock() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            /**
             * My module description.
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Insert @file tag"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * @file
             *
             * My module description.
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )
    }

    fun testQuickFixInsertsFileTagIntoEmptyDocBlock() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            /**
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Insert @file tag"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * @file
             */

            function my_module_hook(): void {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the full docblock fix twice changes nothing further
    // -------------------------------------------------------------------------

    fun testInsertDocBlockFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "my_module.module",
            """
            <?php

            function my_module_hook(): void {}
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Insert @file docblock"
        }
        myFixture.launchAction(firstIntention)
        val afterFirst = myFixture.file.text

        // Second pass — no insert-docblock action should be available.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Insert @file docblock"
        }
        assertTrue(
            "After inserting the docblock, the fix must not be offered again",
            secondIntentions.isEmpty()
        )

        assertEquals(afterFirst, myFixture.file.text)
    }
}
