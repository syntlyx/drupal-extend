package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression

internal object ShortArraySyntaxRule : DrupalCodeStyleRule {
    override val id: String = "short_array_syntax"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleShortArraySyntaxEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val arrayExpression = element as? ArrayCreationExpression ?: return
        if (!arrayExpression.isTopMostTraditionalArraySyntax()) return

        val keyword = arrayExpression.firstChild ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = keyword,
                message = "array() literals must use short array syntax",
                fixes = PhpStormShortArraySyntaxFixes.quickFixes()
            )
        )
    }
}

internal fun ArrayCreationExpression.isTraditionalArraySyntax(): Boolean {
    if (isShortSyntax) return false
    return firstChild?.node?.elementType == PhpTokenTypes.kwARRAY
}

private fun ArrayCreationExpression.isTopMostTraditionalArraySyntax(): Boolean {
    if (!isTraditionalArraySyntax()) return false

    var current = parent
    while (current != null) {
        if (current is ArrayCreationExpression && current.isTraditionalArraySyntax()) {
            return false
        }
        current = current.parent
    }

    return true
}
