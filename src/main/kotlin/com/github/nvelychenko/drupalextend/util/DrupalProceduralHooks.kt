package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter

object DrupalProceduralHooks {
    fun isHookFunction(function: Function): Boolean {
        if (!function.project.drupalExtendSettings.isEnabled) return false
        if (function is Method || function.isClosure) return false

        return function.containingFile.name.substringAfterLast('.', missingDelimiterValue = "") in HOOK_FILE_EXTENSIONS
    }

    fun isHookParameter(parameter: Parameter): Boolean {
        val function = PsiTreeUtil.getParentOfType(parameter, Function::class.java) ?: return false

        return isHookFunction(function)
    }

    private val HOOK_FILE_EXTENSIONS = setOf("module", "install", "theme")
}
