package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpUseList

internal object UseStatementSortingRule : DrupalCodeStyleRule {
    override val id: String = "use_statement_sorting"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleUseStatementSortingEnabled
    }

    override fun inspect(element: com.intellij.psi.PsiElement, context: DrupalCodeStyleContext) {
        val useList = element as? PhpUseList ?: return
        val edit = useList.useStatementSortingEdit() ?: return
        val block = useList.containingFile.sortableUseStatementBlocks()
            .firstOrNull { it.uses.firstOrNull() == useList }
            ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = block.uses.first(),
                message = "Use statements must be sorted alphabetically",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = block.uses.first().relativeRange(edit.startOffset, edit.endOffset),
                fixes = listOf(sortUseStatementsFix())
            )
        )
    }

    private fun sortUseStatementsFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Sort use statements") { _, descriptor ->
            val useList = descriptor.psiElement as? PhpUseList ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            useList.useStatementSortingEdit()?.let { listOf(it) } ?: emptyList()
        }
    }
}

internal fun PsiFile.sortUseStatementEdits(): List<TextEdit> {
    return sortableUseStatementBlocks()
        .mapNotNull { it.sortingEdit() }
}

private fun PhpUseList.useStatementSortingEdit(): TextEdit? {
    val block = containingFile.sortableUseStatementBlocks()
        .firstOrNull { it.uses.firstOrNull() == this }
        ?: return null

    return block.sortingEdit()
}

private data class UseStatementBlock(
    val document: Document,
    val uses: List<PhpUseList>,
)

private fun PsiFile.sortableUseStatementBlocks(): List<UseStatementBlock> {
    val document = document() ?: return emptyList()
    val uses = PsiTreeUtil.findChildrenOfType(this, PhpUseList::class.java)
        .filter { it.isSortableClassUseStatement() }
        .sortedBy { it.textRange.startOffset }
    if (uses.size < 2) return emptyList()

    val blocks = mutableListOf<MutableList<PhpUseList>>()
    uses.forEach { useList ->
        val previousBlock = blocks.lastOrNull()
        val previous = previousBlock?.lastOrNull()
        if (previous != null && document.lineNumber(useList) == document.lineNumber(previous) + 1) {
            previousBlock += useList
        } else {
            blocks += mutableListOf(useList)
        }
    }

    return blocks
        .filter { it.size > 1 }
        .map { UseStatementBlock(document, it) }
}

private fun UseStatementBlock.sortingEdit(): TextEdit? {
    val sortedUses = uses.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.sortKey() ?: it.text })
    if (sortedUses.map { it.text } == uses.map { it.text }) return null

    val firstLine = document.lineNumber(uses.first())
    val lastLine = document.lineNumber(uses.last())
    val startOffset = document.getLineStartOffset(firstLine)
    val endOffset = document.getLineEndOffset(lastLine)
    val indentation = document.lineText(firstLine).takeWhile { it == ' ' || it == '\t' }
    val replacement = sortedUses.joinToString(document.lineSeparator()) { indentation + it.text.trim() }

    return TextEdit(startOffset, endOffset, replacement)
}

private fun PhpUseList.isSortableClassUseStatement(): Boolean {
    if (isTraitImport || isOfConst || isOfFunction) return false
    if (text.contains("{") || text.contains("\n") || text.contains("\r")) return false
    if (declarations.size != 1) return false

    return sortKey() != null
}

private fun PhpUseList.sortKey(): String? {
    val declaration = declarations.singleOrNull() ?: return null
    val target = declaration.targetReference?.fqn
        ?: declaration.text.substringAfter("use", "").substringBefore(";").trim()
    if (target.isBlank()) return null

    return target.removePrefix("\\")
}
