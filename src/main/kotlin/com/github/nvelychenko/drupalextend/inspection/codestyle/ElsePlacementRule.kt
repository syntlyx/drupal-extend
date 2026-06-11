package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.ElseIf

internal object ElsePlacementRule : DrupalCodeStyleRule {
    override val id: String = "else_placement"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleElsePlacementEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        // Two PSI shapes: `else` (and two-keyword `else if`) is an Else; the one-keyword
        // `elseif` is an ElseIf routed through the CONTROL_STATEMENT target.
        if (element !is Else && element !is ElseIf) return
        val edit = element.elsePlacementEdit() ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = "else/elseif must start on a new line",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(moveElseToNewLineFix())
            )
        )
    }

    private fun moveElseToNewLineFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Move else/elseif to a new line") { _, descriptor ->
            val element = descriptor.psiElement
                ?.takeIf { it is Else || it is ElseIf }
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOfNotNull(element.elsePlacementEdit())
        }
    }
}

internal fun PsiElement.elsePlacementEdit(): TextEdit? {
    val fileText = containingFile?.text ?: return null
    val elseStart = textRange.startOffset

    // Walk backward from the start of `else`/`elseif` over horizontal whitespace.
    var offset = elseStart - 1
    while (offset >= 0 && fileText[offset].isWhitespace()) {
        if (fileText[offset] == '\n' || fileText[offset] == '\r') {
            // Already on a new line — no violation.
            return null
        }
        offset--
    }

    // The non-whitespace char immediately before the gap must be `}`.
    // Anything else (e.g. alternative-syntax endif, or start of file) → skip.
    if (offset < 0 || fileText[offset] != '}') return null

    val closingBraceOffset = offset
    val gapStart = closingBraceOffset + 1
    val gap = fileText.substring(gapStart, elseStart)

    // If a comment sits in the gap, never move it.
    if (gap.contains("//") || gap.contains('#') || gap.contains("/*")) return null

    // The indent to use is the leading whitespace on the line where the parent `if` starts.
    val ifIndent = parentIfIndent(fileText) ?: ""

    val replacement = "\n$ifIndent"

    // Idempotency: the gap is already exactly "\n$ifIndent" (cannot happen here since we
    // returned null for newlines above, but guard for CR+LF edge cases just in case).
    if (gap == replacement) return null

    return TextEdit(gapStart, elseStart, replacement)
}

/**
 * Returns the leading whitespace of the line on which the parent `if` statement begins.
 * The parent of an `Else`/`ElseIf` PSI element is always the enclosing `if` statement.
 */
private fun PsiElement.parentIfIndent(fileText: String): String? {
    val ifElement = parent ?: return ""
    val ifStart = ifElement.textRange?.startOffset ?: return ""

    // Scan backward to the start of the line.
    var lineStart = ifStart
    while (lineStart > 0 && fileText[lineStart - 1] != '\n' && fileText[lineStart - 1] != '\r') {
        lineStart--
    }

    // Collect the leading whitespace characters on that line.
    val sb = StringBuilder()
    var i = lineStart
    while (i < ifStart && fileText[i].isWhitespace()) {
        sb.append(fileText[i])
        i++
    }

    return sb.toString()
}
