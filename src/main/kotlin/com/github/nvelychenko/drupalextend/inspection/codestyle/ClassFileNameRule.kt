package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass

internal object ClassFileNameRule : DrupalCodeStyleRule {
    override val id: String = "class_file_name"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleClassFileNameEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val phpClass = element as? PhpClass ?: return
        if (phpClass.isAnonymous) return

        val phpFile = phpClass.containingFile as? PhpFile ?: return
        val expectedClassName = phpFile.name.removeSuffix(".php")
        if (expectedClassName.isBlank()) return
        if (phpClass.name == expectedClassName) return

        context.register(
            DrupalCodeStyleProblem(
                element = phpClass.nameNode?.psi ?: phpClass,
                message = "Class name doesn't match filename; expected \"class $expectedClassName\"",
                highlightType = ProblemHighlightType.WEAK_WARNING
            )
        )
    }
}
