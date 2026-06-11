package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList
import com.jetbrains.php.lang.psi.elements.PhpModifierList

internal object ScopeKeywordSpacingRule : DrupalCodeStyleRule {
    override val id: String = "scope_keyword_spacing"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleScopeKeywordSpacingEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val edits = element.scopeKeywordSpacingEdits()
        val firstBadEdit = edits.firstOrNull { element.relativeRange(it.startOffset, it.endOffset) != null }
            ?: edits.firstOrNull()
            ?: return
        context.register(
            DrupalCodeStyleProblem(
                element = element,
                message = "Scope keywords must be separated by a single space",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = element.relativeRange(firstBadEdit.startOffset, firstBadEdit.endOffset),
                fixes = listOf(normalizeScopeKeywordSpacingFix()),
            )
        )
    }

    private fun normalizeScopeKeywordSpacingFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Normalize scope keyword spacing") { _, descriptor ->
            descriptor.psiElement.scopeKeywordSpacingEdits()
        }
    }
}

internal fun PsiElement.scopeKeywordSpacingEdits(): List<TextEdit> {
    return when (this) {
        is Method -> methodScopeKeywordSpacingEdits()
        is PhpClassFieldsList -> fieldScopeKeywordSpacingEdits()
        else -> emptyList()
    }
}

private fun Method.methodScopeKeywordSpacingEdits(): List<TextEdit> {
    val modifierList = PsiTreeUtil.getChildOfType(this, PhpModifierList::class.java) ?: return emptyList()
    val fileText = containingFile?.text ?: return emptyList()
    return modifierList.scopeKeywordGapEdits(fileText)
}

private fun PhpClassFieldsList.fieldScopeKeywordSpacingEdits(): List<TextEdit> {
    val modifierList = modifierList ?: return emptyList()
    val fileText = containingFile?.text ?: return emptyList()
    return modifierList.scopeKeywordGapEdits(fileText)
}

/**
 * Collects TextEdits for every gap between a scope keyword and the immediately following
 * token.  Checks both intra-modifier-list gaps (keyword → keyword) and the final gap
 * from the last keyword to the first token after the modifier list (e.g. `function`,
 * a type hint, or the `$` sigil of a field).
 *
 * Gaps spanning a newline or containing a comment are left untouched.
 */
private fun PhpModifierList.scopeKeywordGapEdits(fileText: String): List<TextEdit> {
    val edits = mutableListOf<TextEdit>()

    // Collect keyword PSI leaves in source order.
    val keywordLeaves = mutableListOf<PsiElement>()
    var child = firstChild
    while (child != null) {
        if (child.text in scopeKeywords) keywordLeaves += child
        child = child.nextSibling
    }
    if (keywordLeaves.isEmpty()) return emptyList()

    // Check each keyword: gap to the first non-whitespace sibling within the modifier list.
    for (keyword in keywordLeaves) {
        val edit = intraModifierListGapEdit(keyword, fileText)
        if (edit != null) edits += edit
    }

    // Check the last keyword's gap to the first token after the modifier list.
    val lastKeyword = keywordLeaves.last()
    val afterModifierList = nextNonWhitespaceSiblingOnParent() ?: return edits
    // Only proceed if there is no comment between the last keyword and that token.
    if (!hasCommentBetween(lastKeyword.textRange.endOffset, afterModifierList.textRange.startOffset, fileText)) {
        singleSpaceGapEdit(fileText, lastKeyword.textRange.endOffset, afterModifierList.textRange.startOffset)
            ?.let { edits += it }
    }

    return edits
}

/**
 * Returns the first non-whitespace PSI sibling of this modifier list on its parent element.
 */
private fun PhpModifierList.nextNonWhitespaceSiblingOnParent(): PsiElement? {
    var sibling = nextSibling
    while (sibling is PsiWhiteSpace) {
        sibling = sibling.nextSibling
    }
    return sibling
}

/**
 * Checks the gap from [keyword]'s end to the start of its next non-whitespace sibling
 * within the same modifier list (not crossing the modifier list boundary).
 *
 * Returns null when:
 * - there is no next sibling within the modifier list (keyword is the last child),
 * - a comment intervenes, or
 * - the gap is already one space or spans a newline.
 */
private fun intraModifierListGapEdit(keyword: PsiElement, fileText: String): TextEdit? {
    var sibling = keyword.nextSibling
    // Walk over whitespace within the modifier list.
    while (sibling is PsiWhiteSpace) sibling = sibling.nextSibling
    // No intra-list sibling found — last keyword; handled separately.
    if (sibling == null) return null
    // Comment between keywords — skip.
    if (sibling is PsiComment) return null

    return singleSpaceGapEdit(fileText, keyword.textRange.endOffset, sibling.textRange.startOffset)
}

/**
 * Returns true if the file text between [startOffset] and [endOffset] contains any
 * comment-opening sequence (double-slash, slash-star, or hash).
 */
private fun hasCommentBetween(startOffset: Int, endOffset: Int, fileText: String): Boolean {
    if (startOffset >= endOffset) return false
    val region = fileText.substring(startOffset, endOffset)
    return region.contains("//") || region.contains("/*") || region.contains('#')
}

/**
 * Returns a TextEdit replacing the gap [gapStart, gapEnd) with a single space, or null
 * when no edit is needed (gap already correct, spans a newline).
 */
private fun singleSpaceGapEdit(fileText: String, gapStart: Int, gapEnd: Int): TextEdit? {
    if (gapStart >= gapEnd) return null
    if (gapStart < 0 || gapEnd > fileText.length) return null
    val gap = fileText.substring(gapStart, gapEnd)
    if (gap.any { it == '\n' || it == '\r' }) return null
    if (gap == " ") return null
    return TextEdit(gapStart, gapEnd, " ")
}

private val scopeKeywords = setOf(
    "public",
    "protected",
    "private",
    "static",
    "abstract",
    "final",
    "readonly",
    "var",
)
