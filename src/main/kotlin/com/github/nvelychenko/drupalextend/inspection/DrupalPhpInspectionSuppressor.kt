package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.DrupalProceduralHooks
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpPropertyHook

class DrupalPhpInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (!element.project.drupalExtendSettings.isEnabled) return false

        if (toolId in PHP_HOOK_UNUSED_INSPECTIONS && element.isDrupalHookUsageElement()) {
            return true
        }

        if (toolId in PHP_UNUSED_PARAMETER_INSPECTIONS && element.isPropertyHookParameter()) {
            return true
        }

        if (toolId !in DRUPAL_CLASS_FQN_INSPECTIONS) return false

        val classReference = element.drupalRootClassReference() ?: return false

        return classReference.text.trim() == "\\Drupal"
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
        return SuppressQuickFix.EMPTY_ARRAY
    }

    private fun PsiElement.drupalRootClassReference(): ClassReference? {
        return this as? ClassReference ?: PsiTreeUtil.getParentOfType(this, ClassReference::class.java, false)
    }

    private fun PsiElement.isDrupalHookUsageElement(): Boolean {
        val parameter = this as? Parameter ?: PsiTreeUtil.getParentOfType(this, Parameter::class.java, false)
        if (parameter != null) return DrupalProceduralHooks.isHookParameter(parameter)

        val function = this as? Function ?: PsiTreeUtil.getParentOfType(this, Function::class.java, false)
        if (function == null || !DrupalProceduralHooks.isHookFunction(function)) return false

        return this == function || this == function.nameNode?.psi
    }

    private fun PsiElement.isPropertyHookParameter(): Boolean {
        val parameter = this as? Parameter ?: PsiTreeUtil.getParentOfType(this, Parameter::class.java, false)
        if (parameter == null || !parameter.name.equals("value", ignoreCase = true)) return false

        return PsiTreeUtil.getParentOfType(parameter, PhpPropertyHook::class.java, true) != null
    }

    private val PHP_HOOK_UNUSED_INSPECTIONS = setOf(
        "PhpUnused",
        "PhpDeadCode",
        "PhpUnusedParameter",
        "PhpUnusedParameterInspection",
    )

    private val PHP_UNUSED_PARAMETER_INSPECTIONS = setOf(
        "PhpUnusedParameter",
        "PhpUnusedParameterInspection",
    )

    private val DRUPAL_CLASS_FQN_INSPECTIONS = setOf(
        "PhpUnnecessaryFullyQualifiedNameInspection",
        "PhpFullyQualifiedNameUsageInspection",
    )
}
