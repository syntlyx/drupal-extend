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
}
