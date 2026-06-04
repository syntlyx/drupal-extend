package com.github.nvelychenko.drupalextend.action

import com.github.nvelychenko.drupalextend.inspection.codestyle.DrupalCodeStyleFileFixer
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.php.lang.psi.PhpFile

class FixDrupalCodeStyleAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val settings = project?.drupalExtendSettings

        event.presentation.isEnabledAndVisible = project != null &&
            file is PhpFile &&
            settings?.isEnabled == true &&
            settings.isCodeStyleLinterEnabled
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) as? PhpFile ?: return
        val settings = project.drupalExtendSettings
        if (!settings.isEnabled || !settings.isCodeStyleLinterEnabled) return

        WriteCommandAction.writeCommandAction(project, file)
            .withName("Fix Drupal Code Style")
            .run<RuntimeException> {
                DrupalCodeStyleFileFixer.reformatAndApply(project, file)
            }
    }
}
