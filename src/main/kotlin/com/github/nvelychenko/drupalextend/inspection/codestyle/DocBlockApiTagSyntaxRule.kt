package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object DocBlockApiTagSyntaxRule : DrupalCodeStyleRule {
    override val id: String = "docblock_api_tags"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleDocBlockApiTagsEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val docComment = element as? PhpDocComment ?: return
        if (docComment.isInlineVariableDocBlock()) return

        val lines = docComment.drupalDocBlockLines()
        lines.forEachIndexed { index, line ->
            when (line.tagName()) {
                "throws" -> inspectThrowsTag(docComment, line, context)
                "see" -> inspectSeeTag(docComment, line, context)
                "deprecated" -> inspectDeprecatedTag(docComment, lines, index, line, context)
            }
        }
    }

    private fun inspectThrowsTag(
        docComment: PhpDocComment,
        line: DrupalDocBlockLine,
        context: DrupalCodeStyleContext,
    ) {
        val tag = line.throwsTag()
        if (tag == null || tag.type.isBlank()) {
            context.registerDocBlockProblem(
                docComment,
                line,
                "@throws tag must include an exception class"
            )
            return
        }

        if (!tag.type.startsWith("\\")) {
            context.registerDocBlockProblem(
                docComment,
                line,
                "@throws exception class must use a fully qualified namespace",
                prefixThrowsExceptionClassFix()
            )
        }
    }

    private fun prefixThrowsExceptionClassFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Fully qualify @throws exception class") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val line = docComment.drupalDocBlockLines()
                .firstOrNull { it.tagName() == "throws" && it.throwsTag()?.type?.startsWith("\\") == false }
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val tag = line.throwsTag() ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            val typeStart = docComment.textRange.startOffset + line.trimmedContentStartOffset() + tag.typeStartOffset
            val resolvedFqn = docComment.throwsExceptionFqns()[tag.type]
                ?: return@DrupalCodeStyleTextEditQuickFix listOf(TextEdit(typeStart, "\\"))

            val typeEnd = docComment.textRange.startOffset + line.trimmedContentStartOffset() + tag.typeEndOffset
            listOf(TextEdit(typeStart, typeEnd, resolvedFqn))
        }
    }

    private fun inspectSeeTag(
        docComment: PhpDocComment,
        line: DrupalDocBlockLine,
        context: DrupalCodeStyleContext,
    ) {
        val tag = line.seeTag() ?: return
        if (tag.reference.isNullOrBlank()) {
            context.registerDocBlockProblem(
                docComment,
                line,
                "@see tag must include a reference"
            )
            return
        }

        if (!tag.extraText.isNullOrBlank()) {
            context.registerDocBlockProblem(
                docComment,
                line,
                "@see tag must contain only one reference"
            )
        }
    }

    private fun inspectDeprecatedTag(
        docComment: PhpDocComment,
        lines: List<DrupalDocBlockLine>,
        index: Int,
        line: DrupalDocBlockLine,
        context: DrupalCodeStyleContext,
    ) {
        val deprecatedText = lines.deprecatedText(index)
        if (!deprecatedText.matches(DRUPAL_DEPRECATED_TEXT)) {
            context.registerDocBlockProblem(
                docComment,
                line,
                "@deprecated tag must use Drupal deprecation text format"
            )
        }

        if (!lines.hasFollowingSeeTag(index)) {
            context.registerDocBlockProblem(
                docComment,
                line,
                "@deprecated tag must be followed by @see"
            )
        }
    }

    private fun List<DrupalDocBlockLine>.deprecatedText(index: Int): String {
        val content = mutableListOf(get(index).normalizedTrimmedContent())
        drop(index + 1)
            .takeWhile { !it.isTag && !it.isBlankContent }
            .mapTo(content) { it.normalizedTrimmedContent() }

        return content.joinToString(" ") { it.trim() }
    }

    private fun List<DrupalDocBlockLine>.hasFollowingSeeTag(index: Int): Boolean {
        for (lineIndex in index + 1 until size) {
            val line = get(lineIndex)
            if (line.isBlankContent || line.isOpeningLine() || line.isClosingLine()) continue
            if (!line.isTag) continue

            return line.tagName() == "see"
        }

        return false
    }

    private val DRUPAL_DEPRECATED_TEXT =
        Regex("""^@deprecated in \S+ and is removed from \S+\. .+\S\.$""")
}
