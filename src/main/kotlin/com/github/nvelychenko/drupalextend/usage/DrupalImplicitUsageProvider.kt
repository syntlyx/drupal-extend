package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.util.DrupalFieldPlugins
import com.github.nvelychenko.drupalextend.util.DrupalProceduralHooks
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.Services
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpClass

class DrupalImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (DumbService.isDumb(element.project)) return false

        return when (element) {
            is PhpClass -> isImplicitPhpClassUsage(element)
            is Method -> isImplicitMethodUsage(element)
            is Function -> DrupalProceduralHooks.isHookFunction(element)
            is Parameter -> DrupalProceduralHooks.isHookParameter(element)
            else -> false
        }
    }

    override fun isImplicitRead(element: PsiElement): Boolean {
        return element is Parameter && DrupalProceduralHooks.isHookParameter(element)
    }

    override fun isImplicitWrite(element: PsiElement): Boolean = false

    private fun isImplicitPhpClassUsage(phpClass: PhpClass): Boolean {
        val project = phpClass.project

        return DrupalFieldPlugins.referencesClass(project, phpClass) ||
                Services.referencesClass(project, phpClass.fqn) ||
                Routes.referencesHandler(project, phpClass.fqn)
    }

    private fun isImplicitMethodUsage(method: Method): Boolean {
        val phpClass = method.containingClass ?: return false

        return Services.referencesMethod(method.project, phpClass.fqn, method.name) ||
                Routes.referencesHandler(method.project, phpClass.fqn, method.name)
    }
}
