package com.github.nvelychenko.drupalextend.action

import com.github.nvelychenko.drupalextend.inspection.codestyle.DrupalCodeStyleFileFixer
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FixDrupalCodeStyleActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        // The light fixture shares the settings service across test classes; per-rule tests
        // disable rules globally, so pin every rule this test's assertions depend on.
        project.drupalExtendSettings.isCodeStyleConstantCasingEnabled = true
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = true
        project.drupalExtendSettings.isCodeStyleClosingPhpTagEnabled = true
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = true
    }

    fun testActionUpdateRunsInBackgroundThread() {
        assertEquals(ActionUpdateThread.BGT, FixDrupalCodeStyleAction().actionUpdateThread)
    }

    fun testReformatsAndAppliesDrupalCodeStyleFixes() {
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(array ${'$'}items): array {
                foreach(${'$'}items as${'$'}key=>${'$'}value){
                  if(${'$'}key){
                    return [true,false,null];
                  }else{
                    return ${'$'}items;
                  }
                }
              }
            }
            ?>
            """.trimIndent()
        )

        WriteCommandAction.runWriteCommandAction(project) {
            DrupalCodeStyleFileFixer.reformatAndApply(project, myFixture.file)
        }

        val text = myFixture.file.text
        assertTrue(text.contains("foreach (${'$'}items as ${'$'}key => ${'$'}value) {"))
        assertTrue(text.contains("if (${'$'}key) {"))
        assertTrue(text.contains("} else {"))
        assertTrue(text.contains("return [TRUE, FALSE, NULL];"))
        assertFalse(text.trimEnd().endsWith("?>"))
    }

    fun testConvertsDoubleQuotedStringsToSingleQuotes() {
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(string ${'$'}name): array {
              ${'$'}plain = "hello";
              ${'$'}escaped = "say \"hi\"";
              ${'$'}interpolated = "hello ${'$'}name";
              ${'$'}withNewline = "hello\n";
              ${'$'}withApostrophe = "it's fine";
              return [${'$'}plain, ${'$'}escaped, ${'$'}interpolated, ${'$'}withNewline, ${'$'}withApostrophe];
            }
            """.trimIndent()
        )

        WriteCommandAction.runWriteCommandAction(project) {
            DrupalCodeStyleFileFixer.apply(project, myFixture.file)
        }

        val text = myFixture.file.text
        assertTrue(text.contains("${'$'}plain = 'hello';"))
        assertTrue(text.contains("${'$'}escaped = 'say \"hi\"';"))
        // Interpolation, double-quote-only escapes, and embedded apostrophes must stay double-quoted.
        assertTrue(text.contains("${'$'}interpolated = \"hello ${'$'}name\";"))
        assertTrue(text.contains("${'$'}withNewline = \"hello\\n\";"))
        assertTrue(text.contains("${'$'}withApostrophe = \"it's fine\";"))
    }

    fun testDoesNotConvertAnnotationStringsInsideDocBlocks() {
        myFixture.configureByText(
            "CustomFieldDiffBuilder.php",
            """
            <?php

            /**
             * Plugin to diff entity reference fields.
             *
             * @FieldDiffBuilder(
             *   id = "custom_field_diff_builder",
             *   label = @Translation("Custom Field Diff"),
             *   field_types = {
             *     "bank_information", "credit_grade",
             *   },
             * )
             */
            class CustomFieldDiffBuilder {}
            """.trimIndent()
        )

        WriteCommandAction.runWriteCommandAction(project) {
            DrupalCodeStyleFileFixer.apply(project, myFixture.file)
        }

        val text = myFixture.file.text
        // Doctrine/Drupal annotations only accept double-quoted strings — the fixer
        // must leave doc-block contents untouched.
        assertTrue(text.contains("id = \"custom_field_diff_builder\""))
        assertTrue(text.contains("@Translation(\"Custom Field Diff\")"))
        assertTrue(text.contains("\"bank_information\", \"credit_grade\","))
    }
}
