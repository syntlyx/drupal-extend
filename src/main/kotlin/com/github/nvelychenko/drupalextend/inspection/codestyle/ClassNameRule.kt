package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpClass

internal object ClassNameRule : DrupalCodeStyleRule {
    override val id: String = "class_name"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleClassNameEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val phpClass = element as? PhpClass ?: return
        if (phpClass.isAnonymous) return

        // An empty name is normal while the declaration is being typed.
        val name: String? = phpClass.name
        if (name.isNullOrBlank()) return
        if (name.isPascalCaseName()) return

        context.register(
            DrupalCodeStyleProblem(
                element = phpClass.nameNode?.psi ?: phpClass,
                message = "${phpClass.kindLabel()} name should use PascalCase",
                highlightType = ProblemHighlightType.WEAK_WARNING
            )
        )
    }

    private fun PhpClass.kindLabel(): String {
        return when {
            isInterface -> "Interface"
            isTrait -> "Trait"
            isEnum -> "Enum"
            else -> "Class"
        }
    }
}

internal fun String.isPascalCaseName(): Boolean {
    return PASCAL_CASE_NAME.matches(this)
}

private val PASCAL_CASE_NAME = Regex("""[A-Z][A-Za-z0-9]*""")
