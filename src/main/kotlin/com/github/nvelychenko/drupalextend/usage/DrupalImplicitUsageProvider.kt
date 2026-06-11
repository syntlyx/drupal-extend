package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
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
import com.jetbrains.php.lang.psi.elements.PhpAttributesOwner
import com.jetbrains.php.lang.psi.elements.PhpClass

class DrupalImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (DumbService.isDumb(element.project)) return false

        return when (element) {
            is PhpClass -> isImplicitPhpClassUsage(element)
            is Method -> isImplicitMethodUsage(element)
            is Function -> DrupalProceduralHooks.isHookFunction(element) || element.hasHookAttribute()
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

        // A class hosting OOP hook implementations is instantiated by the hook system.
        return phpClass.ownMethods.any { it.hasHookAttribute() } ||
                DrupalFieldPlugins.referencesClass(project, phpClass) ||
                Services.referencesClass(project, phpClass.fqn) ||
                Routes.referencesHandler(project, phpClass.fqn)
    }

    private fun isImplicitMethodUsage(method: Method): Boolean {
        if (method.hasHookAttribute()) return true
        val phpClass = method.containingClass ?: return false

        return Services.referencesMethod(method.project, phpClass.fqn, method.name) ||
                Routes.referencesHandler(method.project, phpClass.fqn, method.name)
    }

    /**
     * Covers the whole Drupal 11.x hook-attribute family: `#[Hook]` methods are invoked by the
     * hook system, `#[LegacyHook]` marks BC procedural shims, and `#[ReorderHook]`/`#[RemoveHook]`
     * carriers participate in hook ordering even without an own `#[Hook]`.
     */
    private fun PhpAttributesOwner.hasHookAttribute(): Boolean {
        if (!project.drupalExtendSettings.isEnabled) return false

        return attributes.any { it.fqn in HOOK_ATTRIBUTE_FQNS }
    }
}

private val HOOK_ATTRIBUTE_FQNS = setOf(
    "\\Drupal\\Core\\Hook\\Attribute\\Hook",
    "\\Drupal\\Core\\Hook\\Attribute\\LegacyHook",
    "\\Drupal\\Core\\Hook\\Attribute\\ReorderHook",
    "\\Drupal\\Core\\Hook\\Attribute\\RemoveHook",
)
