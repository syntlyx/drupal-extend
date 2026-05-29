package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression

internal object PhpStormShortArraySyntaxFixes {
    private const val INSPECTION_CLASS = "com.jetbrains.php.lang.inspections.PhpTraditionalSyntaxArrayLiteralInspection"
    private const val CONVERT_BASE_CLASS = "com.jetbrains.php.lang.intentions.array.PhpConvertArraySyntaxBase"

    fun quickFixes(): List<LocalQuickFix> {
        val fixes = runCatching {
            Class.forName(INSPECTION_CLASS)
                .getMethod("createFixes")
                .invoke(null)
        }.getOrNull() as? Array<*> ?: return listOf(convertArrayToShortSyntaxQuickFix)

        return fixes.filterIsInstance<LocalQuickFix>()
            .ifEmpty { listOf(convertArrayToShortSyntaxQuickFix) }
    }

    fun applyToFile(file: PsiFile): Boolean {
        val arrays = PsiTreeUtil.findChildrenOfType(file, ArrayCreationExpression::class.java)
            .asSequence()
            .filter { it.isTraditionalArraySyntax() }
            .sortedByDescending { it.textRange.startOffset }
            .toList()

        if (arrays.isEmpty()) return false

        val document = file.document() ?: return false
        val beforeText = document.text
        arrays.forEach { applyToArray(it) }
        PsiDocumentManager.getInstance(file.project).doPostponedOperationsAndUnblockDocument(document)

        return beforeText != document.text
    }

    private fun applyToArray(arrayExpression: ArrayCreationExpression): Boolean {
        if (!arrayExpression.isValid || !arrayExpression.isTraditionalArraySyntax()) return false

        return runCatching {
            Class.forName(CONVERT_BASE_CLASS)
                .getMethod("changeSyntax", ArrayCreationExpression::class.java)
                .invoke(null, arrayExpression)
            true
        }.getOrDefault(false)
    }

    private val convertArrayToShortSyntaxQuickFix = object : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Convert array to short syntax"
        }

        override fun getName(): String {
            return familyName
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val arrayExpression = PsiTreeUtil.getParentOfType(
                descriptor.psiElement,
                ArrayCreationExpression::class.java,
                false
            ) ?: return

            applyToArray(arrayExpression)
            val document = arrayExpression.containingFile?.document() ?: return
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        }
    }
}
