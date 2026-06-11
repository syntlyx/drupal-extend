package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.inspection.codestyle.DrupalCodeStyleRuleRegistry
import com.github.nvelychenko.drupalextend.inspection.codestyle.DrupalCodeStyleContext
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * [CleanupLocalInspectionTool]: the rules' quick fixes also run through the IDE's standard
 * Code Cleanup (Code → Code Cleanup…, and the "Cleanup code" checkbox in the commit dialog),
 * so Drupal style is appliable as part of the normal commit workflow.
 */
class DrupalCodeStyleInspection : LocalInspectionTool(), CleanupLocalInspectionTool {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        val settings = project.drupalExtendSettings

        if (!settings.isEnabled) return emptyVisitor
        if (!settings.isCodeStyleLinterEnabled) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        val dispatcher = DrupalCodeStyleRuleRegistry.dispatcher(settings)
        if (dispatcher.isEmpty) return emptyVisitor
        val context = DrupalCodeStyleContext(holder, isOnTheFly)

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                dispatcher.inspect(element, context)
            }
        }
    }
}
