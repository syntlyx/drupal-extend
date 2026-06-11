package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal interface DrupalCodeStyleRule {
    val id: String

    fun isEnabled(settings: DrupalExtendSettings): Boolean

    fun inspect(element: PsiElement, context: DrupalCodeStyleContext)
}

internal data class DrupalCodeStyleContext(
    val holder: ProblemsHolder,
    val isOnTheFly: Boolean,
    /**
     * The user-configured severity of the registering rule. Set by the dispatcher; replaces
     * the rule's own highlight type so severity is controlled in exactly one place.
     */
    val severityOverride: ProblemHighlightType? = null,
) {
    fun withSeverityOverride(severity: ProblemHighlightType?): DrupalCodeStyleContext {
        if (severity == null || severity == severityOverride) return this

        return copy(severityOverride = severity)
    }

    fun register(problem: DrupalCodeStyleProblem) {
        val problemWithFileFix = problem
            .let { if (severityOverride != null) it.copy(highlightType = severityOverride) else it }
            .withFileFix()
        if (problemWithFileFix.rangeInElement != null) {
            holder.registerProblem(
                problemWithFileFix.element,
                problemWithFileFix.message,
                problemWithFileFix.highlightType,
                problemWithFileFix.rangeInElement,
                *problemWithFileFix.fixes.toTypedArray()
            )
            return
        }

        holder.registerProblem(
            problemWithFileFix.element,
            problemWithFileFix.message,
            problemWithFileFix.highlightType,
            *problemWithFileFix.fixes.toTypedArray()
        )
    }
}

internal data class DrupalCodeStyleProblem(
    val element: PsiElement,
    val message: String,
    val highlightType: ProblemHighlightType = ProblemHighlightType.WEAK_WARNING,
    val rangeInElement: TextRange? = null,
    val fixes: List<LocalQuickFix> = emptyList(),
    val includeFileFix: Boolean = true,
)

private fun DrupalCodeStyleProblem.withFileFix(): DrupalCodeStyleProblem {
    if (!includeFileFix) return this
    if (fixes.isEmpty() || fixes.any { it.name == DrupalCodeStyleFixes.fixFile.name }) return this

    return copy(fixes = fixes + DrupalCodeStyleFixes.fixFile)
}

internal fun PsiElement.relativeRangeOf(child: PsiElement): TextRange? {
    return relativeRange(child.textRange.startOffset, child.textRange.endOffset)
}

internal fun PsiElement.relativeRange(startOffset: Int, endOffset: Int): TextRange? {
    if (startOffset < textRange.startOffset || endOffset > textRange.endOffset || startOffset >= endOffset) {
        return null
    }

    return TextRange(startOffset - textRange.startOffset, endOffset - textRange.startOffset)
}

internal fun interface DrupalCodeStyleEditBuilder {
    fun buildEdits(project: Project, descriptor: ProblemDescriptor): Collection<TextEdit>
}

internal class DrupalCodeStyleTextEditQuickFix(
    private val label: String,
    @FileModifier.SafeFieldForPreview
    private val editBuilder: DrupalCodeStyleEditBuilder,
) : LocalQuickFix {
    override fun getFamilyName(): String {
        return label
    }

    override fun getName(): String {
        return label
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val edits = editBuilder.buildEdits(project, descriptor)
        if (edits.isEmpty()) return

        edits.applyTo(document)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

internal fun DrupalCodeStyleContext.registerDocBlockProblem(
    docComment: PhpDocComment,
    line: DrupalDocBlockLine,
    message: String,
    vararg fixes: LocalQuickFix,
) {
    val quickFixes = fixes.toList()
        .withGroupedDocBlockFixes()
    register(
        DrupalCodeStyleProblem(
            element = docComment,
            message = message,
            rangeInElement = line.problemRange(),
            fixes = quickFixes
        )
    )
}

private fun List<LocalQuickFix>.withGroupedDocBlockFixes(): List<LocalQuickFix> {
    if (isEmpty()) return this

    return this + DrupalDocBlockFixes.fixDocBlock
}

private fun DrupalDocBlockLine.problemRange(): TextRange? {
    val startOffset = if (contentStartOffset < lineEndOffset) {
        contentStartOffset
    } else {
        lineStartOffset
    }

    if (startOffset >= lineEndOffset) return null
    return TextRange(startOffset, lineEndOffset)
}
