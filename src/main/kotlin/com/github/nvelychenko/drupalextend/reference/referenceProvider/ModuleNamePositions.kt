package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Shared, allocation-light detection of PHP positions where a string literal is a module
 * machine name. Used by both [ModuleNameReferenceProvider] and the completion provider so they
 * agree on context. Every check casts the immediate parent first and bails O(1) off the broad
 * `StringLiteralExpression` pattern.
 */
internal enum class ModuleNamePositionKind {
    /** First argument of a module-handler/-installer `moduleExists`/`install`/... call. */
    MODULE_CALL,

    /** First argument of a `themeExists` call (allows theme-typed receivers). */
    THEME_CALL,

    /** A string element of a test class' `protected static $modules = [...]` array. */
    MODULES_FIELD,
}

private val MODULE_METHOD_NAMES = setOf("moduleexists", "install", "uninstall")
private const val THEME_METHOD_NAME = "themeexists"

private val MODULE_RECEIVER_HINTS = listOf(
    "modulehandler", "module_handler", "moduleinstaller", "module_installer",
)
private val THEME_RECEIVER_HINTS = listOf(
    "modulehandler", "module_handler", "themehandler", "theme_handler",
)

/** Classifies the literal's module-name position, or null when it is not one. */
internal fun StringLiteralExpression.moduleNamePositionKind(): ModuleNamePositionKind? {
    methodCallKind()?.let { return it }
    if (isModulesFieldElement()) return ModuleNamePositionKind.MODULES_FIELD
    return null
}

/** True when the literal is the first argument of a module-name method call we recognise. */
private fun StringLiteralExpression.methodCallKind(): ModuleNamePositionKind? {
    val parameterList = parent as? ParameterList ?: return null
    if (parameterList.parameters.firstOrNull()?.isEquivalentTo(this) != true) return null

    val methodReference = parameterList.parent as? MethodReference ?: return null
    val name = methodReference.name?.lowercase() ?: return null

    if (name in MODULE_METHOD_NAMES) {
        return if (methodReference.hasModuleHandlerOrInstallerReceiver()) {
            ModuleNamePositionKind.MODULE_CALL
        } else {
            null
        }
    }

    if (name == THEME_METHOD_NAME) {
        return if (methodReference.hasThemeishReceiver()) {
            ModuleNamePositionKind.THEME_CALL
        } else {
            null
        }
    }

    return null
}

private fun MethodReference.hasModuleHandlerOrInstallerReceiver(): Boolean =
    receiverHints(MODULE_RECEIVER_HINTS)

private fun MethodReference.hasThemeishReceiver(): Boolean =
    receiverHints(THEME_RECEIVER_HINTS)

/**
 * Heuristic on the receiver text (mirrors [com.github.nvelychenko.drupalextend.util.isContainerGetCall]'s
 * approach): a lowercased receiver containing a known handler/installer token, or a
 * `\Drupal::moduleHandler()` / `service('module_handler')` style chain.
 */
private fun MethodReference.receiverHints(hints: List<String>): Boolean {
    val receiver = classReference?.text?.lowercase() ?: return false
    return hints.any { it in receiver }
}

/** True when the literal is a string element of a `$modules` class field array. */
private fun StringLiteralExpression.isModulesFieldElement(): Boolean {
    val array = PsiTreeUtil.getParentOfType(this, ArrayCreationExpression::class.java) ?: return false
    val field = PsiTreeUtil.getParentOfType(array, Field::class.java) ?: return false
    if (field.name != "modules") return false
    // Guard against matching nested arrays whose owning field merely happens to be $modules:
    // the literal must live inside the field's own default-value array, not some unrelated one.
    return PsiTreeUtil.isAncestor(field, array, true)
}
