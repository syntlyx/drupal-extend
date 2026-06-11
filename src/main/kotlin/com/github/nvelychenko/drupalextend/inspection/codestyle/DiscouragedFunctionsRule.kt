package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Statement
import com.jetbrains.php.lang.psi.elements.StatementWithArgument
import com.jetbrains.php.lang.psi.elements.StatementWithArguments

internal object DiscouragedFunctionsRule : DrupalCodeStyleRule {
    override val id: String = "discouraged_functions"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDiscouragedFunctionsEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        if (element is MethodReference) return

        val call = element as? FunctionReference ?: return
        val name = call.name?.lowercase() ?: return

        if (name !in DISCOURAGED_FUNCTIONS) return

        if (name == "print_r" && call.isPrintRReturnMode()) return

        val statement = call.standaloneStatement()
        val fixes = if (statement != null) listOf(removeDebugCallFix()) else emptyList()

        context.register(
            DrupalCodeStyleProblem(
                element = call,
                message = "Debug function '$name' should not be committed",
                highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                fixes = fixes,
                includeFileFix = false,
            )
        )
    }

    private fun removeDebugCallFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Remove debug call") { _, descriptor ->
            val call = descriptor.psiElement as? FunctionReference ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val statement = call.standaloneStatement() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOfNotNull(statement.removeStatementEdit())
        }
    }
}

private val DISCOURAGED_FUNCTIONS = setOf(
    "dpm", "dsm", "dvm", "dpr", "kpr", "ksm", "dd", "kint",
    "var_dump", "dump", "ddebug_backtrace", "print_r",
)

private fun FunctionReference.isPrintRReturnMode(): Boolean {
    val params = parameters
    if (params.size < 2) return false
    val second = params[1] as? ConstantReference ?: return false
    return second.name?.uppercase() == "TRUE"
}

private fun FunctionReference.standaloneStatement(): PsiElement? {
    val parent = parent ?: return null
    if (parent !is Statement) return null
    if (parent is StatementWithArgument) return null
    if (parent is StatementWithArguments) return null
    return parent
}

private fun PsiElement.removeStatementEdit(): TextEdit? {
    val fileText = containingFile?.text ?: return null
    val statementStart = textRange.startOffset
    val statementEnd = textRange.endOffset

    // Walk backwards from statementStart to consume leading whitespace on the same line.
    var removeStart = statementStart
    while (removeStart > 0 && fileText[removeStart - 1].let { it == ' ' || it == '\t' }) {
        removeStart--
    }

    // Walk forwards from statementEnd to consume trailing semicolon already part of the
    // statement node, then consume the newline so no blank line is left.
    var removeEnd = statementEnd
    if (removeEnd < fileText.length && fileText[removeEnd] == '\r') removeEnd++
    if (removeEnd < fileText.length && fileText[removeEnd] == '\n') removeEnd++

    if (removeStart == statementStart && removeEnd == statementEnd) return null

    return TextEdit(removeStart, removeEnd, "")
}
