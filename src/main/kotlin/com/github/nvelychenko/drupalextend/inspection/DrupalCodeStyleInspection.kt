package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.inspection.codestyle.DrupalCodeStyleRuleRegistry
import com.github.nvelychenko.drupalextend.inspection.codestyle.DrupalCodeStyleContext
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

class DrupalCodeStyleInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        val settings = project.drupalExtendSettings

        if (!settings.isEnabled) return emptyVisitor
        if (!settings.isCodeStyleLinterEnabled) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        val rules = DrupalCodeStyleRuleRegistry.rules.filter { it.isEnabled(settings) }
        if (rules.isEmpty()) return emptyVisitor
        val context = DrupalCodeStyleContext(holder, isOnTheFly)

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                rules.forEach { it.inspect(element, context) }
            }
        }
    }
}
