package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.inspections.phpdoc.GeneratePhpDocQuickFix
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement

internal object MissingDocCommentRule : DrupalCodeStyleRule {
    override val id: String = "missing_doc_comment"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleMissingDocCommentsEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        when (element) {
            is PhpClass -> inspectClass(element, context)
            is Function -> inspectFunction(element, context)
        }
    }

    private fun inspectClass(phpClass: PhpClass, context: DrupalCodeStyleContext) {
        if (phpClass.isAnonymous) return
        if (phpClass.docComment != null) return

        registerMissingDocCommentProblem(phpClass, "Missing class doc comment", context)
    }

    private fun inspectFunction(function: Function, context: DrupalCodeStyleContext) {
        if (function.isClosure) return
        if (function.docComment != null) return

        registerMissingDocCommentProblem(function, "Missing function doc comment", context)
    }

    private fun registerMissingDocCommentProblem(
        element: PhpNamedElement,
        message: String,
        context: DrupalCodeStyleContext,
    ) {
        val nameElement = element.nameNode?.psi ?: element
        val fix = if (context.isOnTheFly) GeneratePhpDocQuickFix.ON_THE_FLY else GeneratePhpDocQuickFix.INSTANCE

        context.register(
            DrupalCodeStyleProblem(
                element = nameElement,
                message = message,
                highlightType = ProblemHighlightType.WEAK_WARNING,
                fixes = listOf(fix)
            )
        )
    }
}
