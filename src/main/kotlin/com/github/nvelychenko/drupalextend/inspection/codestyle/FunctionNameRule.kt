package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method

internal object FunctionNameRule : DrupalCodeStyleRule {
    override val id: String = "function_name"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleFunctionNameEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val name = function.name
        val problem = when (function) {
            is Method -> methodNameProblem(name)
            else -> functionNameProblem(name)
        } ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = function.nameNode?.psi ?: function,
                message = problem,
                highlightType = ProblemHighlightType.WEAK_WARNING
            )
        )
    }

    private fun methodNameProblem(name: String): String? {
        if (name in PHP_MAGIC_METHODS) return null
        if (name.isCamelCaseName()) return null

        return "Method name should use camelCase"
    }

    private fun functionNameProblem(name: String): String? {
        if (name.isSnakeCaseName()) return null

        return "Function name should use snake_case"
    }
}

internal fun String.isCamelCaseName(): Boolean {
    return CAMEL_CASE_NAME.matches(this)
}

internal fun String.isSnakeCaseName(): Boolean {
    return SNAKE_CASE_NAME.matches(this)
}

private val CAMEL_CASE_NAME = Regex("""[a-z][A-Za-z0-9]*""")
private val SNAKE_CASE_NAME = Regex("""[a-z][a-z0-9]*(?:_[a-z0-9]+)*""")

private val PHP_MAGIC_METHODS = setOf(
    "__construct",
    "__destruct",
    "__call",
    "__callStatic",
    "__get",
    "__set",
    "__isset",
    "__unset",
    "__sleep",
    "__wakeup",
    "__serialize",
    "__unserialize",
    "__toString",
    "__invoke",
    "__set_state",
    "__clone",
    "__debugInfo",
)
