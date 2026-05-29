package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.PhpFile

internal object ClosingPhpTagRule : DrupalCodeStyleRule {
    override val id: String = "closing_php_tag"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleClosingPhpTagEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val file = element as? PhpFile ?: return
        val edit = file.closingPhpTagEdit() ?: return
        val closingTag = file.phpClosingTagAtEnd() ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = closingTag,
                message = "PHP-only files must not end with a closing tag",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = closingTag.relativeRange(closingTag.textRange.startOffset, closingTag.textRange.endOffset),
                fixes = listOf(removeClosingPhpTagFix())
            )
        )
    }

    private fun removeClosingPhpTagFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Remove closing PHP tag") { _, descriptor ->
            descriptor.psiElement.containingFile?.closingPhpTagEdit()?.let { listOf(it) }
                ?: emptyList()
        }
    }
}

internal fun PsiFile.closingPhpTagEdit(): TextEdit? {
    val closingTag = phpClosingTagAtEnd() ?: return null
    val startOffset = closingTag.startOffsetIncludingOwnTrailingLine()

    return TextEdit(startOffset, textLength, "")
}

private fun PsiFile.phpClosingTagAtEnd(): PsiElement? {
    val closingTag = depthFirstElements()
        .lastOrNull { it.node.elementType == PhpTokenTypes.PHP_CLOSING_TAG }
        ?: return null
    if (text.substring(closingTag.textRange.endOffset).isNotBlank()) return null

    return closingTag
}

private fun PsiElement.startOffsetIncludingOwnTrailingLine(): Int {
    val fileText = containingFile.text
    val closingStartOffset = textRange.startOffset
    val lineStartOffset = fileText.lastIndexOf('\n', (closingStartOffset - 1).coerceAtLeast(0))
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: 0
    val prefix = fileText.substring(lineStartOffset, closingStartOffset)
    if (lineStartOffset == 0 || prefix.isNotBlank()) return closingStartOffset

    val previousLineSeparatorStart = if (lineStartOffset >= 2 && fileText[lineStartOffset - 2] == '\r') {
        lineStartOffset - 2
    } else {
        lineStartOffset - 1
    }

    return previousLineSeparatorStart.coerceAtLeast(0)
}

private fun PsiElement.depthFirstElements(): Sequence<PsiElement> {
    return sequence {
        yield(this@depthFirstElements)

        var child = firstChild
        while (child != null) {
            yieldAll(child.depthFirstElements())
            child = child.nextSibling
        }
    }
}
