package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.MethodReference

class DrupalDependencyInjectionInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!isSupportEnabled(project)) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is MethodReference) return

                val serviceCall = DrupalStaticServiceCall.from(project, element) ?: return
                val phpClass = serviceCall.targetClassForInjection() ?: return
                if (phpClass.findOwnFieldByName(serviceCall.propertyName, false) != null) return

                holder.registerProblem(
                    element,
                    "Use constructor injection for service '${serviceCall.serviceId}'",
                    ProblemHighlightType.WEAK_WARNING,
                    InjectDrupalServiceQuickFix(serviceCall.serviceId)
                )
            }
        }
    }

    private fun isSupportEnabled(project: Project): Boolean {
        val settings = project.drupalExtendSettings

        return settings.isEnabled
                && settings.isDependencyInjectionHelperEnabled
    }
}
