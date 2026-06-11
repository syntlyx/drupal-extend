package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.ModuleReference
import com.github.nvelychenko.drupalextend.util.Modules
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Navigation from a module machine name string to its `*.info.yml`. Covers
 * `moduleHandler()->moduleExists('node')`, `moduleInstaller()->install('node')`,
 * `themeExists('olivero')`, and test `protected static $modules = ['node']` arrays.
 *
 * Registered on a broad `StringLiteralExpression` pattern; bails O(1) when the literal is not
 * in a recognised module-name position.
 */
class ModuleNameReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        // Pure-PSI gates first; the settings service lookup runs only for literals that are
        // actually in a module-name position (this provider sees every string literal).
        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        if (literal.contents.isEmpty()) return PsiReference.EMPTY_ARRAY
        if (literal.moduleNamePositionKind() == null) return PsiReference.EMPTY_ARRAY

        if (!Modules.isSupportEnabled(literal.project)) return PsiReference.EMPTY_ARRAY

        return arrayOf(ModuleReference(literal, literal.contents, literal.contentRange()))
    }
}
