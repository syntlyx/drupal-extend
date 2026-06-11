package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment

internal object InlineCommentRule : DrupalCodeStyleRule {
    override val id: String = "inline_comment"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleInlineCommentEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val comment = element as? PsiComment ?: return
        // Docblocks are routed elsewhere; defensive guard.
        if (comment is PhpDocComment) return

        val text = comment.text

        // Block comments /* ... */ that are not docblocks: skip entirely.
        if (text.startsWith("/*")) return

        // Check 1: # style → rewrite to //.
        if (text.startsWith("#")) {
            val rest = text.substring(1)
            context.register(
                DrupalCodeStyleProblem(
                    element = comment,
                    message = "Inline comments should use '//' not '#'",
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    fixes = listOf(rewriteHashCommentFix(rest))
                )
            )
            return
        }

        // Only // comments below this point.
        if (!text.startsWith("//")) return

        val afterSlashes = text.substring(2)

        // Check 2: // must be followed by a space (skip if afterSlashes is blank/empty).
        if (afterSlashes.isNotEmpty() && afterSlashes.isNotBlank() && !afterSlashes.startsWith(" ")) {
            context.register(
                DrupalCodeStyleProblem(
                    element = comment,
                    message = "Inline comment '//' must be followed by a space",
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    fixes = listOf(insertSpaceAfterSlashesFix())
                )
            )
            return
        }

        // Trailing comments (code before this comment on the same line): only checks 1 and 2.
        if (isTrailingComment(comment)) return

        // Checks 3 and 4 only apply to standalone // comments that form a run.
        val document = comment.document() ?: return
        val myLine = document.lineNumber(comment)

        val isFirstOfRun = !hasPrecedingRunComment(comment, myLine, document)
        val isLastOfRun = !hasFollowingRunComment(comment, myLine, document)

        val content = afterSlashes.trim()

        // Check 3: first comment of a run should start with a capital letter.
        if (isFirstOfRun && content.isNotEmpty()) {
            val firstChar = content[0]
            if (firstChar.isLowerCase() && firstChar in 'a'..'z' && !shouldSkipCapitalizationAndPunctuation(content)) {
                context.register(
                    DrupalCodeStyleProblem(
                        element = comment,
                        message = "Inline comment should start with a capital letter",
                        highlightType = ProblemHighlightType.WEAK_WARNING,
                        fixes = listOf(capitalizeFirstLetterFix())
                    )
                )
            }
        }

        // Check 4: last comment of a run should end with terminal punctuation.
        if (isLastOfRun && content.isNotEmpty() && !shouldSkipCapitalizationAndPunctuation(content)) {
            val lastChar = content.last()
            if (lastChar !in TERMINAL_PUNCTUATION) {
                // Skip if last char looks like code (ends with ;, {, }, )).
                if (lastChar !in CODE_ENDING_CHARS) {
                    context.register(
                        DrupalCodeStyleProblem(
                            element = comment,
                            message = "Inline comment should end with punctuation",
                            highlightType = ProblemHighlightType.WEAK_WARNING,
                            fixes = listOf(appendPeriodFix())
                        )
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns true when `content` should be skipped for checks 3 and 4. */
    private fun shouldSkipCapitalizationAndPunctuation(content: String): Boolean {
        if (content.isEmpty()) return true
        val firstChar = content[0]
        // Starts with @, -, *.
        if (firstChar == '@' || firstChar == '-' || firstChar == '*') return true
        // phpcs: / cspell: / eslint / noinspection directives.
        if (content.contains("phpcs:") || content.contains("cspell:") ||
            content.contains("eslint") || content.contains("noinspection")
        ) return true
        return false
    }

    /** Returns true when the comment is not the first non-whitespace content on its line. */
    private fun isTrailingComment(comment: PsiComment): Boolean {
        val document = comment.document() ?: return false
        val myLine = document.lineNumber(comment)
        val lineStart = document.getLineStartOffset(myLine)
        val commentStart = comment.textRange.startOffset
        if (commentStart <= lineStart) return false

        // Check if there is any non-whitespace content on this line before the comment.
        val linePrefix = document.charsSequence.subSequence(lineStart, commentStart).toString()
        return linePrefix.isNotBlank()
    }

    /** Returns true when the previous // comment is immediately adjacent (line - 1). */
    private fun hasPrecedingRunComment(comment: PsiComment, myLine: Int, document: com.intellij.openapi.editor.Document): Boolean {
        var sibling = comment.prevSibling
        while (sibling != null) {
            if (sibling is PsiWhiteSpace) {
                // Allow only whitespace that does not span more than one extra line.
                // If whitespace contains 2+ newlines, the run is broken.
                val newlines = sibling.text.count { it == '\n' }
                if (newlines > 1) return false
                sibling = sibling.prevSibling
                continue
            }
            if (sibling is PhpDocComment) return false
            if (sibling is PsiComment) {
                val sibText = sibling.text
                if (!sibText.startsWith("//")) return false
                val sibLine = document.lineNumber(sibling)
                return sibLine == myLine - 1
            }
            return false
        }
        return false
    }

    /** Returns true when the next // comment is immediately adjacent (line + 1). */
    private fun hasFollowingRunComment(comment: PsiComment, myLine: Int, document: com.intellij.openapi.editor.Document): Boolean {
        var sibling = comment.nextSibling
        while (sibling != null) {
            if (sibling is PsiWhiteSpace) {
                val newlines = sibling.text.count { it == '\n' }
                if (newlines > 1) return false
                sibling = sibling.nextSibling
                continue
            }
            if (sibling is PhpDocComment) return false
            if (sibling is PsiComment) {
                val sibText = sibling.text
                if (!sibText.startsWith("//")) return false
                val sibLine = document.lineNumber(sibling)
                return sibLine == myLine + 1
            }
            return false
        }
        return false
    }

    // -------------------------------------------------------------------------
    // Quick-fix builders
    // -------------------------------------------------------------------------

    private fun rewriteHashCommentFix(rest: String): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Replace '#' comment with '//'") { _, descriptor ->
            val el = descriptor.psiElement as? PsiComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val t = el.text
            if (!t.startsWith("#")) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val newText = "//${t.substring(1)}"
            listOf(TextEdit(el.textRange.startOffset, el.textRange.endOffset, newText))
        }
    }

    private fun insertSpaceAfterSlashesFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Insert space after '//'") { _, descriptor ->
            val el = descriptor.psiElement as? PsiComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val t = el.text
            if (!t.startsWith("//")) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val afterSlashes = t.substring(2)
            if (afterSlashes.isEmpty() || afterSlashes.startsWith(" ")) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val insertAt = el.textRange.startOffset + 2
            listOf(TextEdit(insertAt, insertAt, " "))
        }
    }

    private fun capitalizeFirstLetterFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Capitalize inline comment") { _, descriptor ->
            val el = descriptor.psiElement as? PsiComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val t = el.text
            if (!t.startsWith("//")) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val afterSlashes = t.substring(2)
            val contentStart = afterSlashes.indexOfFirst { !it.isWhitespace() }
            if (contentStart < 0) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val absolutePos = el.textRange.startOffset + 2 + contentStart
            val ch = t[2 + contentStart]
            if (!ch.isLowerCase() || ch !in 'a'..'z') return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOf(TextEdit(absolutePos, absolutePos + 1, ch.uppercaseChar().toString()))
        }
    }

    private fun appendPeriodFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Add terminal punctuation to inline comment") { _, descriptor ->
            val el = descriptor.psiElement as? PsiComment ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val t = el.text
            if (!t.startsWith("//")) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val content = t.substring(2).trim()
            if (content.isEmpty()) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val lastChar = content.last()
            if (lastChar in TERMINAL_PUNCTUATION || lastChar in CODE_ENDING_CHARS) return@DrupalCodeStyleTextEditQuickFix emptyList()
            val insertAt = el.textRange.endOffset
            listOf(TextEdit(insertAt, insertAt, "."))
        }
    }

    private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', ':', '…')
    private val CODE_ENDING_CHARS = setOf(';', '{', '}', ')')
}
