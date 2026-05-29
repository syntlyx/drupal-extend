package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.DoWhile
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.PhpSwitch

internal object ControlStructureSpacingRule : DrupalCodeStyleRule {
    override val id: String = "control_structure_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleControlStructureSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        if (element !is ControlStatement && element !is PhpSwitch && element !is Else) return

        val edits = element.controlStructureSpacingEdits()
        val edit = edits.firstOrNull { element.relativeRange(it.startOffset, it.endOffset) != null }
            ?: edits.firstOrNull()
            ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = "Control structures must use Drupal spacing",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(normalizeControlStructureSpacingFix())
            )
        )
    }

    private fun normalizeControlStructureSpacingFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize control structure spacing") { _, descriptor ->
            descriptor.psiElement.controlStructureSpacingEdits()
        }
    }
}

internal fun PsiElement.controlStructureSpacingEdits(): List<TextEdit> {
    if (this is DoWhile) return emptyList()

    val edits = mutableListOf<TextEdit>()
    val keyword = controlKeyword()
    if (this is ControlStatement || this is PhpSwitch) {
        keyword?.let { controlKeywordToParenthesisSpacingEdit(it) }?.let { edits += it }
        keyword?.let { closingParenthesisToOpeningBraceSpacingEdit(it) }?.let { edits += it }
    }

    if (this is ForeachStatement) {
        foreachAsSpacingEdits().forEach { edits += it }
    }

    if (this is Else) {
        keyword?.let { elseToOpeningBraceSpacingEdit(it) }?.let { edits += it }
    }

    keyword?.let { previousClosingBraceToKeywordSpacingEdit(it) }?.let { edits += it }

    return edits
}

private data class ControlKeyword(
    val value: String,
    val startOffset: Int,
) {
    val endOffset: Int
        get() = startOffset + value.length
}

private fun PsiElement.controlKeyword(): ControlKeyword? {
    val fileText = containingFile.text
    val lineStartOffset = fileText.lastIndexOf('\n', (textRange.startOffset - 1).coerceAtLeast(0))
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: 0
    val lineEndOffset = fileText.indexOf('\n', lineStartOffset)
        .takeIf { it >= 0 }
        ?: fileText.length

    for (startOffset in lineStartOffset until lineEndOffset) {
        val keyword = controlKeywords.firstOrNull { keyword ->
            fileText.regionMatches(startOffset, keyword, 0, keyword.length) &&
                fileText.getOrNull(startOffset - 1)?.isPhpIdentifierPart() != true &&
                fileText.getOrNull(startOffset + keyword.length)?.isPhpIdentifierPart() != true
        } ?: continue

        return ControlKeyword(keyword, startOffset)
    }

    return null
}

private fun PsiElement.controlKeywordToParenthesisSpacingEdit(keyword: ControlKeyword): TextEdit? {
    val leftParenthesis = nextNonWhitespaceSameLine(keyword.endOffset)
        ?.takeIf { containingFile.text.getOrNull(it) == '(' }
        ?: return null

    return singleLineGapEdit(keyword.endOffset, leftParenthesis, " ")
}

private fun PsiElement.closingParenthesisToOpeningBraceSpacingEdit(keyword: ControlKeyword): TextEdit? {
    val fileText = containingFile.text
    val leftParenthesis = nextNonWhitespaceSameLine(keyword.endOffset)
        ?.takeIf { fileText.getOrNull(it) == '(' }
        ?: return null
    val rightParenthesis = matchingRightParenthesisOffset(leftParenthesis) ?: return null
    val openingBrace = nextNonWhitespaceSameLine(rightParenthesis + 1)
        ?.takeIf { fileText.getOrNull(it) == '{' }
        ?: return null

    return singleLineGapEdit(rightParenthesis + 1, openingBrace, " ")
}

private fun ForeachStatement.foreachAsSpacingEdits(): List<TextEdit> {
    val array = array ?: return emptyList()
    val value = value ?: return emptyList()
    val key = key
    val edits = mutableListOf<TextEdit>()

    val firstVariable = key ?: value
    singleLineGapEdit(array.textRange.endOffset, firstVariable.textRange.startOffset, " as ")
        ?.let { edits += it }

    if (key != null) {
        singleLineGapEdit(key.textRange.endOffset, value.textRange.startOffset, " => ")
            ?.let { edits += it }
    }

    return edits
}

private fun PsiElement.elseToOpeningBraceSpacingEdit(keyword: ControlKeyword): TextEdit? {
    if (keyword.value != "else") return null

    val openingBrace = nextNonWhitespaceSameLine(keyword.endOffset)
        ?.takeIf { containingFile.text.getOrNull(it) == '{' }
        ?: return null

    return singleLineGapEdit(keyword.endOffset, openingBrace, " ")
}

private fun PsiElement.previousClosingBraceToKeywordSpacingEdit(keyword: ControlKeyword): TextEdit? {
    val fileText = containingFile.text
    var offset = keyword.startOffset - 1
    while (offset >= 0 && fileText[offset].isWhitespace() && fileText[offset] != '\n' && fileText[offset] != '\r') {
        offset--
    }

    if (offset < 0 || fileText[offset] != '}') return null

    return singleLineGapEdit(offset + 1, keyword.startOffset, " ")
}

private fun PsiElement.singleLineGapEdit(
    startOffset: Int,
    endOffset: Int,
    expectedGap: String,
): TextEdit? {
    if (startOffset > endOffset) return null
    val gap = containingFile.text.substring(startOffset, endOffset)
    if (gap.any { it == '\n' || it == '\r' }) return null
    if (gap == expectedGap) return null

    return TextEdit(startOffset, endOffset, expectedGap)
}

private fun PsiElement.nextNonWhitespaceSameLine(startOffset: Int): Int? {
    val fileText = containingFile.text
    var offset = startOffset
    while (offset < fileText.length && fileText[offset].isWhitespace()) {
        if (fileText[offset] == '\n' || fileText[offset] == '\r') return null
        offset++
    }

    return offset
}

private fun PsiElement.matchingRightParenthesisOffset(leftParenthesis: Int): Int? {
    val fileText = containingFile.text
    var depth = 0
    var offset = leftParenthesis
    while (offset < textRange.endOffset) {
        when (fileText[offset]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return offset
            }
        }
        offset++
    }

    return null
}

private fun Char.isPhpIdentifierPart(): Boolean {
    return isLetterOrDigit() || this == '_'
}

private val controlKeywords = listOf(
    "foreach",
    "elseif",
    "switch",
    "while",
    "catch",
    "else",
    "for",
    "if",
)
