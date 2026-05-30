package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList

internal object MemberVisibilityRule : DrupalCodeStyleRule {
    override val id: String = "member_visibility"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleMemberVisibilityEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val problem = when (element) {
            is Method -> element.memberVisibilityProblem()
            is PhpClassFieldsList -> element.memberVisibilityEdit()?.let {
                MemberVisibilityProblem(
                    message = "Class properties must declare visibility",
                    edit = it,
                )
            }
            is PhpClass -> element.classConstantVisibilityProblems().firstOrNull()?.let {
                MemberVisibilityProblem(
                    message = "Class constants must declare visibility",
                    edit = it.edit,
                    rangeStartOffset = it.rangeStartOffset,
                    rangeEndOffset = it.rangeEndOffset,
                )
            }
            else -> null
        } ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = problem.message,
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(problem.rangeStartOffset, problem.rangeEndOffset),
                fixes = listOf(addPublicVisibilityFix())
            )
        )
    }

    private fun addPublicVisibilityFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Add explicit public visibility") { _, descriptor ->
            val element = descriptor.psiElement
            when (element) {
                is Method -> element.memberVisibilityEdit()?.let { listOf(it) }
                is PhpClassFieldsList -> element.memberVisibilityEdit()?.let { listOf(it) }
                is PhpClass -> element.classConstantVisibilityProblems().map { it.edit }
                else -> emptyList()
            } ?: emptyList()
        }
    }
}

private data class MemberVisibilityProblem(
    val message: String,
    val edit: TextEdit,
    val rangeStartOffset: Int = edit.startOffset,
    val rangeEndOffset: Int = edit.endOffset,
)

private data class ClassConstantVisibilityProblem(
    val edit: TextEdit,
    val rangeStartOffset: Int,
    val rangeEndOffset: Int,
)

private fun Method.memberVisibilityProblem(): MemberVisibilityProblem? {
    val functionOffset = functionKeywordOffset() ?: return null
    val lineStartOffset = containingFile.text.lineStartOffset(functionOffset)
    val edit = containingFile.text.publicVisibilityEditBefore(lineStartOffset, functionOffset) ?: return null

    return MemberVisibilityProblem(
        message = "Class methods must declare visibility",
        edit = edit,
        rangeStartOffset = functionOffset,
        rangeEndOffset = functionOffset + "function".length,
    )
}

internal fun Method.memberVisibilityEdit(): TextEdit? {
    val functionOffset = functionKeywordOffset() ?: return null
    val lineStartOffset = containingFile.text.lineStartOffset(functionOffset)

    return containingFile.text.publicVisibilityEditBefore(lineStartOffset, functionOffset)
}

private fun Method.functionKeywordOffset(): Int? {
    val nameStartOffset = nameNode?.psi?.textRange?.startOffset ?: return null
    val lineStartOffset = containingFile.text.lineStartOffset(nameStartOffset)
    return containingFile.text.indexOfStandaloneKeyword("function", lineStartOffset, nameStartOffset)
}

internal fun PhpClassFieldsList.memberVisibilityEdit(): TextEdit? {
    val firstField = fields.minByOrNull { it.textRange.startOffset } ?: return null
    if (fields.all { it.isConstant }) return null

    val nameStartOffset = firstField.nameNode?.psi?.textRange?.startOffset ?: firstField.textRange.startOffset
    val lineStartOffset = containingFile.text.lineStartOffset(nameStartOffset)
    val varOffset = containingFile.text.indexOfStandaloneKeyword("var", lineStartOffset, nameStartOffset)
    if (varOffset != null) {
        return TextEdit(varOffset, varOffset + "var".length, "public")
    }

    val modifierList = modifierList
    if (modifierList?.hasPublic() == true || modifierList?.hasProtected() == true || modifierList?.hasPrivate() == true) {
        return null
    }

    val declarationOffset = listOf("const", "readonly", "static")
        .mapNotNull { keyword -> containingFile.text.indexOfStandaloneKeyword(keyword, lineStartOffset, nameStartOffset) }
        .minOrNull()
        ?: containingFile.text.firstNonWhitespaceOffset(lineStartOffset, nameStartOffset)
        ?: return null

    return containingFile.text.publicVisibilityEditBefore(lineStartOffset, declarationOffset)
}

internal fun PhpClass.classConstantVisibilityEdits(): List<TextEdit> {
    return classConstantVisibilityProblems().map { it.edit }
}

private fun PhpClass.classConstantVisibilityProblems(): List<ClassConstantVisibilityProblem> {
    val fileText = containingFile.text
    val classStartOffset = textRange.startOffset
    val classText = fileText.substring(textRange.startOffset, textRange.endOffset)

    return missingClassConstantVisibility.findAll(classText)
        .mapNotNull { match ->
            val matchStartOffset = classStartOffset + match.range.first
            val constOffset = matchStartOffset + match.value.indexOf("const")
            val edit = fileText.publicVisibilityEditBefore(matchStartOffset, constOffset) ?: return@mapNotNull null
            ClassConstantVisibilityProblem(
                edit = edit,
                rangeStartOffset = constOffset,
                rangeEndOffset = constOffset + "const".length,
            )
        }
        .toList()
}

private fun String.publicVisibilityEditBefore(lineStartOffset: Int, declarationOffset: Int): TextEdit? {
    val prefixStart = firstNonWhitespaceOffset(lineStartOffset, declarationOffset + 1) ?: return null
    if (hasStandaloneKeyword("public", prefixStart, declarationOffset)) return null
    if (hasStandaloneKeyword("protected", prefixStart, declarationOffset)) return null
    if (hasStandaloneKeyword("private", prefixStart, declarationOffset)) return null

    val finalOffset = indexOfStandaloneKeyword("final", prefixStart, declarationOffset)
    val abstractOffset = indexOfStandaloneKeyword("abstract", prefixStart, declarationOffset)
    val visibilityAfterOffset = listOfNotNull(
        finalOffset?.plus("final".length),
        abstractOffset?.plus("abstract".length),
    ).maxOrNull()

    return if (visibilityAfterOffset != null) {
        TextEdit(visibilityAfterOffset, " public")
    } else {
        TextEdit(prefixStart, "public ")
    }
}

private fun String.lineStartOffset(offset: Int): Int {
    return lastIndexOf('\n', (offset - 1).coerceAtLeast(0))
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: 0
}

private fun String.firstNonWhitespaceOffset(startOffset: Int, endOffset: Int): Int? {
    for (offset in startOffset.coerceAtLeast(0) until endOffset.coerceAtMost(length)) {
        if (!this[offset].isWhitespace()) return offset
    }

    return null
}

private fun String.hasStandaloneKeyword(keyword: String, startOffset: Int, endOffset: Int): Boolean {
    return indexOfStandaloneKeyword(keyword, startOffset, endOffset) != null
}

private fun String.indexOfStandaloneKeyword(keyword: String, startOffset: Int, endOffset: Int): Int? {
    val start = startOffset.coerceAtLeast(0)
    val end = endOffset.coerceAtMost(length)
    for (offset in start until end) {
        if (!regionMatches(offset, keyword, 0, keyword.length)) continue
        if (getOrNull(offset - 1)?.isPhpIdentifierPart() == true) continue
        if (getOrNull(offset + keyword.length)?.isPhpIdentifierPart() == true) continue

        return offset
    }

    return null
}

private fun Char.isPhpIdentifierPart(): Boolean {
    return isLetterOrDigit() || this == '_'
}

private val missingClassConstantVisibility = Regex("""(?m)^[ \t]*(?:final[ \t]+)?const\b""")
