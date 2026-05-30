package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Parameter

internal object FunctionDeclarationStyleRule : DrupalCodeStyleRule {
    override val id: String = "function_declaration_style"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleFunctionDeclarationStyleEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val function = element as? Function ?: return
        if (function.isClosure) return

        val document = function.document() ?: return
        function.functionDeclarationStyleIssues(document).forEach { issue ->
            context.register(
                DrupalCodeStyleProblem(
                    element = function,
                    message = issue.message,
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    rangeInElement = function.nameNode?.psi?.let { function.relativeRangeOf(it) },
                    fixes = listOf(normalizeFunctionDeclarationStyleFix())
                )
            )
        }
    }

    private fun normalizeFunctionDeclarationStyleFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize multiline function declaration") { _, descriptor ->
            val function = descriptor.psiElement as? Function ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = function.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            function.functionDeclarationStyleEdits(document)
        }
    }
}

internal fun Function.functionDeclarationStyleEdits(document: Document): List<TextEdit> {
    return functionDeclarationStyleIssues(document)
        .map { it.edit }
        .distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
}

private fun Function.functionDeclarationStyleIssues(document: Document): List<FunctionDeclarationStyleIssue> {
    val parameters = parameters.toList()
    if (parameters.isEmpty()) return emptyList()

    val parentheses = parameterParentheses(document) ?: return emptyList()
    val openParenOffset = parentheses.openOffset
    val closeParenOffset = parentheses.closeOffset
    val openLine = document.getLineNumber(openParenOffset)
    val closeLine = document.getLineNumber(closeParenOffset)
    if (openLine == closeLine) return emptyList()

    val declarationIndent = document.lineIndent(document.getLineNumber(textRange.startOffset))
    val parameterIndent = declarationIndent + "  "
    val lineSeparator = document.lineSeparator()
    val issues = mutableListOf<FunctionDeclarationStyleIssue>()

    val firstParameter = parameters.first()
    val firstParameterLine = document.getLineNumber(firstParameter.textRange.startOffset)
    if (firstParameterLine != openLine + 1) {
        issues += FunctionDeclarationStyleIssue(
            "The first parameter of a multiline function declaration must be on the line after the opening parenthesis",
            TextEdit(openParenOffset + 1, firstParameter.textRange.startOffset, lineSeparator + parameterIndent)
        )
    }

    parameters.zipWithNext().forEach { (previous, current) ->
        if (document.getLineNumber(previous.textRange.startOffset) == document.getLineNumber(current.textRange.startOffset)) {
            oneParameterPerLineEdit(previous, current, document, parameterIndent, lineSeparator)?.let { edit ->
                issues += FunctionDeclarationStyleIssue(
                    "Multiline function declarations must have one parameter per line",
                    edit
                )
            }
        }
    }

    parameters.forEach { parameter ->
        val parameterLine = document.getLineNumber(parameter.textRange.startOffset)
        if (parameterLine == openLine) return@forEach
        val currentIndent = document.lineIndent(parameterLine)
        if (currentIndent == parameterIndent) return@forEach

        issues += FunctionDeclarationStyleIssue(
            "Multiline function declaration parameters must be indented by two spaces",
            TextEdit(
                document.getLineStartOffset(parameterLine),
                document.lineFirstNonWhitespaceOffset(parameterLine),
                parameterIndent
            )
        )
    }

    val lastParameter = parameters.last()
    val lastParameterLine = document.getLineNumber(lastParameter.textRange.endOffset - 1)
    val closeLineEdit = closeParenthesisLineEdit(lastParameter, closeParenOffset, document, declarationIndent, lineSeparator)
    if (!lastParameter.hasTrailingCommaBefore(closeParenOffset, document)) {
        issues += FunctionDeclarationStyleIssue(
            "Multiline function declarations must have a trailing comma after the last parameter",
            if (lastParameterLine == closeLine && closeLineEdit != null) {
                closeLineEdit
            } else {
                TextEdit(lastParameter.textRange.endOffset, ",")
            }
        )
    }

    if (closeLine == lastParameterLine || document.lineText(closeLine).substringBefore(")") != declarationIndent) {
        issues += FunctionDeclarationStyleIssue(
            "The closing parenthesis of a multiline function declaration must be on a new line",
            closeLineEdit ?: TextEdit(
                document.getLineStartOffset(closeLine),
                closeParenOffset,
                declarationIndent
            )
        )
    }

    return issues
}

private data class FunctionDeclarationStyleIssue(
    val message: String,
    val edit: TextEdit,
)

private data class FunctionParentheses(
    val openOffset: Int,
    val closeOffset: Int,
)

private fun Function.parameterParentheses(document: Document): FunctionParentheses? {
    val searchStart = nameNode?.psi?.textRange?.endOffset ?: textRange.startOffset
    val searchEnd = textRange.endOffset.coerceAtMost(document.textLength)
    val openOffset = document.charsSequence.indexOfCharacter('(', searchStart, searchEnd) ?: return null
    val closeOffset = document.charsSequence.matchingParenthesisOffset(openOffset, searchEnd) ?: return null

    return FunctionParentheses(openOffset, closeOffset)
}

private fun oneParameterPerLineEdit(
    previous: Parameter,
    current: Parameter,
    document: Document,
    parameterIndent: String,
    lineSeparator: String,
): TextEdit? {
    val startOffset = previous.textRange.endOffset
    val endOffset = current.textRange.startOffset
    if (startOffset >= endOffset) return null
    val gap = document.charsSequence.subSequence(startOffset, endOffset).toString()
    if (!gap.contains(",")) return null

    return TextEdit(startOffset, endOffset, ",$lineSeparator$parameterIndent")
}

private fun closeParenthesisLineEdit(
    lastParameter: Parameter,
    closeParenOffset: Int,
    document: Document,
    declarationIndent: String,
    lineSeparator: String,
): TextEdit? {
    val lastParameterEndOffset = lastParameter.textRange.endOffset
    if (lastParameterEndOffset > closeParenOffset) return null

    return TextEdit(lastParameterEndOffset, closeParenOffset, ",$lineSeparator$declarationIndent")
}

private fun Parameter.hasTrailingCommaBefore(closeParenOffset: Int, document: Document): Boolean {
    if (textRange.endOffset >= closeParenOffset) return false
    return document.charsSequence
        .subSequence(textRange.endOffset, closeParenOffset)
        .contains(',')
}

private fun CharSequence.indexOfCharacter(character: Char, startOffset: Int, endOffset: Int): Int? {
    val start = startOffset.coerceAtLeast(0)
    val end = endOffset.coerceAtMost(length)
    for (offset in start until end) {
        if (this[offset] == character) return offset
    }

    return null
}

private fun CharSequence.matchingParenthesisOffset(openOffset: Int, endOffset: Int): Int? {
    var depth = 0
    var inSingleQuotedString = false
    var inDoubleQuotedString = false
    var escaped = false
    val end = endOffset.coerceAtMost(length)

    for (offset in openOffset until end) {
        val char = this[offset]
        when {
            escaped -> escaped = false
            char == '\\' && (inSingleQuotedString || inDoubleQuotedString) -> escaped = true
            char == '\'' && !inDoubleQuotedString -> inSingleQuotedString = !inSingleQuotedString
            char == '"' && !inSingleQuotedString -> inDoubleQuotedString = !inDoubleQuotedString
            inSingleQuotedString || inDoubleQuotedString -> Unit
            char == '(' -> depth++
            char == ')' -> {
                depth--
                if (depth == 0) return offset
            }
        }
    }

    return null
}

private fun Document.lineIndent(line: Int): String {
    return lineText(line).takeWhile { it == ' ' || it == '\t' }
}

private fun Document.lineFirstNonWhitespaceOffset(line: Int): Int {
    val lineStart = getLineStartOffset(line)
    val lineEnd = getLineEndOffset(line)
    var offset = lineStart
    while (offset < lineEnd && charsSequence[offset].isWhitespace()) {
        offset++
    }

    return offset
}
