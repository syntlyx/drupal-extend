package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockDeclarationSpacingRule : DrupalCodeStyleRule {
    override val id: String = "docblock_declaration_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockDeclarationSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        if (docComment.isInlineVariableDocBlock()) return

        val document = docComment.document() ?: return
        if (docComment.declarationSpacingEdit(document) == null) return

        context.register(
            DrupalCodeStyleProblem(
                element = docComment,
                message = "DocBlock must be immediately followed by attributes or the documented declaration",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = docComment.closingLineRange(),
                fixes = listOf(removeBlankLineAfterDocBlockFix())
            )
        )
    }

    private fun removeBlankLineAfterDocBlockFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Remove blank line after DocBlock") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val document = docComment.document() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            docComment.declarationSpacingEdit(document)?.let { listOf(it) } ?: emptyList()
        }
    }
}

internal fun PhpDocComment.declarationSpacingEdit(document: Document): TextEdit? {
    val endLine = document.endLineNumber(this)
    if (document.blankLinesAfter(endLine) == 0) return null

    val nextLine = document.nextNonBlankLineNumber(endLine) ?: return null
    if (!document.lineText(nextLine).isDocumentedDeclarationLine()) return null

    return document.removeBlankLinesAfterEdit(endLine)
}

private fun PhpDocComment.closingLineRange(): TextRange? {
    val closingLine = drupalDocBlockLines().lastOrNull { it.isClosingLine() } ?: return null
    return TextRange(closingLine.lineStartOffset, closingLine.lineEndOffset)
}

private fun String.isDocumentedDeclarationLine(): Boolean {
    return DOCUMENTED_DECLARATION_LINE.matches(this)
}

private val DOCUMENTED_DECLARATION_LINE = Regex(
    """^\s*(?:#\[|(?:abstract\s+|final\s+|readonly\s+)*class\b|interface\b|trait\b|enum\b|(?:public|protected|private|static|abstract|final|readonly|var|const|function)\b).*"""
)
