package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.DrupalFieldPlugins
import com.github.nvelychenko.drupalextend.util.DrupalProceduralHooks
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.Services
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpAttributesOwner
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

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

        if (isSubscribedEventMethod(method, phpClass)) return true

        if (isFormCallbackMethod(method, phpClass)) return true

        return Services.referencesMethod(method.project, phpClass.fqn, method.name) ||
                Routes.referencesHandler(method.project, phpClass.fqn, method.name)
    }

    /**
     * Form API registers callbacks by name as `'::handler'` string literals (`#submit`,
     * `#validate`, `#ajax` `callback`, …); the form system invokes them reflectively, so the
     * methods look unused.
     *
     * Performance: collecting every `'::…'` literal in the class once and caching the resulting
     * name set per class (keyed on the containing file) turns this into a single set lookup per
     * method, instead of re-walking the subtree for each method the IDE inspects.
     */
    private fun isFormCallbackMethod(method: Method, phpClass: PhpClass): Boolean {
        if (!method.project.drupalExtendSettings.isEnabled) return false
        return ownFormCallbackNames(phpClass).contains(method.name)
    }

    private fun ownFormCallbackNames(phpClass: PhpClass): Set<String> =
        CachedValuesManager.getCachedValue(phpClass) {
            val names = HashSet<String>()
            for (literal in PsiTreeUtil.findChildrenOfType(phpClass, StringLiteralExpression::class.java)) {
                ProgressManager.checkCanceled()
                val contents = literal.contents
                if (contents.length > FORM_CALLBACK_PREFIX.length && contents.startsWith(FORM_CALLBACK_PREFIX)) {
                    names.add(contents.substring(FORM_CALLBACK_PREFIX.length))
                }
            }
            CachedValueProvider.Result.create(names, phpClass.containingFile)
        }

    /**
     * Symfony/Drupal event subscribers register handler methods by name inside
     * `getSubscribedEvents()`; the dispatcher invokes them reflectively, so they look unused.
     *
     * Cheap by construction: we only do work for classes that actually declare an own
     * `getSubscribedEvents`, and the string search is bounded to that one method's subtree.
     * `getSubscribedEvents` itself is the dispatcher's contract method on
     * `EventSubscriberInterface`, so it is implicitly used too.
     */
    private fun isSubscribedEventMethod(method: Method, phpClass: PhpClass): Boolean {
        if (!method.project.drupalExtendSettings.isEnabled) return false

        val subscribedEvents = phpClass.ownMethods
            .firstOrNull { it.name == SUBSCRIBED_EVENTS_METHOD } ?: return false

        if (method.name == SUBSCRIBED_EVENTS_METHOD) {
            return method.isEquivalentTo(subscribedEvents) && implementsEventSubscriberInterface(phpClass)
        }

        return subscribedEventHandlerNames(subscribedEvents).contains(method.name)
    }

    private fun implementsEventSubscriberInterface(phpClass: PhpClass): Boolean =
        phpClass.interfaceNames.any { it.substringAfterLast('\\') == EVENT_SUBSCRIBER_INTERFACE }

    private fun subscribedEventHandlerNames(subscribedEvents: Method): Set<String> {
        val body = subscribedEvents.lastChild ?: return emptySet()
        val names = HashSet<String>()
        for (literal in PsiTreeUtil.findChildrenOfType(body, StringLiteralExpression::class.java)) {
            ProgressManager.checkCanceled()
            val contents = literal.contents
            if (contents.isNotEmpty()) names.add(contents)
        }
        return names
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

private const val SUBSCRIBED_EVENTS_METHOD = "getSubscribedEvents"
private const val EVENT_SUBSCRIBER_INTERFACE = "EventSubscriberInterface"
private const val FORM_CALLBACK_PREFIX = "::"

private val HOOK_ATTRIBUTE_FQNS = setOf(
    "\\Drupal\\Core\\Hook\\Attribute\\Hook",
    "\\Drupal\\Core\\Hook\\Attribute\\LegacyHook",
    "\\Drupal\\Core\\Hook\\Attribute\\ReorderHook",
    "\\Drupal\\Core\\Hook\\Attribute\\RemoveHook",
)
