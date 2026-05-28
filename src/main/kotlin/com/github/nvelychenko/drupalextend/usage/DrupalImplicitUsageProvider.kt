package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.Services
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

class DrupalImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (DumbService.isDumb(element.project)) return false

        return when (element) {
            is PhpClass -> isImplicitPhpClassUsage(element)
            is Method -> isImplicitMethodUsage(element)
            else -> false
        }
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false

    override fun isImplicitWrite(element: PsiElement): Boolean = false

    private fun isImplicitPhpClassUsage(phpClass: PhpClass): Boolean {
        val project = phpClass.project

        return Services.referencesClass(project, phpClass.fqn) ||
                Routes.referencesHandler(project, phpClass.fqn)
    }

    private fun isImplicitMethodUsage(method: Method): Boolean {
        val phpClass = method.containingClass ?: return false

        return Routes.referencesHandler(method.project, phpClass.fqn, method.name)
    }
}
