package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.PhpFile

// Drupal.Commenting.FileComment: procedural Drupal files (.module, .install,
// .theme, .inc, .profile) must begin with a file-level docblock containing
// the @file tag.
//
// Two violations are possible:
//  - No file-level docblock at all: insert the canonical @file block.
//  - File-level docblock present but missing @file: insert the tag as the
//    first content line of the existing docblock.
internal object FileDocCommentRule : DrupalCodeStyleRule {
    override val id: String = "file_doc_comment"

    // Matches the <?php opening line including its trailing newline.
    private val phpOpenTagLine = Regex("""^<\?php[ \t]*(?:\r?\n|\r)?""")

    override fun isEnabled(settings: DrupalExtendSettings): Boolean =
        settings.isCodeStyleFileDocCommentEnabled

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val file = element as? PhpFile ?: return
        if (!file.isProceduralDrupalFile()) return

        val text = file.text ?: return
        val openTagMatch = phpOpenTagLine.find(text) ?: return
        // Offset immediately after the <?php line — where new content would go.
        val afterOpenTagOffset = openTagMatch.range.last + 1

        // Find the first docblock in the file.
        val firstDocBlock = PsiTreeUtil.findChildOfType(file, PhpDocComment::class.java)

        when {
            firstDocBlock == null -> {
                // No docblock exists: check if there is any non-whitespace content
                // after the open tag. If the file is empty beyond <?php, skip.
                val contentAfterTag = text.substring(afterOpenTagOffset).trimStart()
                if (contentAfterTag.isEmpty()) return

                // Report on the <?php open tag range.
                val openTagRange = file.relativeRange(openTagMatch.range.first, openTagMatch.range.last + 1)
                context.register(
                    DrupalCodeStyleProblem(
                        element = file,
                        message = "Procedural Drupal files must start with a @file docblock",
                        highlightType = ProblemHighlightType.WEAK_WARNING,
                        rangeInElement = openTagRange,
                        fixes = listOf(insertFileDocBlockFix(afterOpenTagOffset))
                    )
                )
            }

            !firstDocBlock.isFileLevelDocBlock(afterOpenTagOffset) -> {
                // There's a docblock, but it's not at the top of the file.
                val openTagRange = file.relativeRange(openTagMatch.range.first, openTagMatch.range.last + 1)
                context.register(
                    DrupalCodeStyleProblem(
                        element = file,
                        message = "Procedural Drupal files must start with a @file docblock",
                        highlightType = ProblemHighlightType.WEAK_WARNING,
                        rangeInElement = openTagRange,
                        fixes = listOf(insertFileDocBlockFix(afterOpenTagOffset))
                    )
                )
            }

            !firstDocBlock.hasFileTag() -> {
                // File-level docblock exists but is missing @file.
                context.register(
                    DrupalCodeStyleProblem(
                        element = firstDocBlock,
                        message = "File docblock is missing the @file tag",
                        highlightType = ProblemHighlightType.WEAK_WARNING,
                        fixes = listOf(insertFileTagFix())
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Quick fixes
    // -------------------------------------------------------------------------

    private fun insertFileDocBlockFix(insertOffset: Int): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Insert @file docblock") { _, descriptor ->
            val file = descriptor.psiElement.containingFile as? PhpFile
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            // Idempotency guard: re-derive offset from current file text.
            val currentText = file.text ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val currentOpenTagMatch = phpOpenTagLine.find(currentText)
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val currentAfterOpenTag = currentOpenTagMatch.range.last + 1

            // If the first docblock already has @file, nothing to do.
            val existingDocBlock = PsiTreeUtil.findChildOfType(file, PhpDocComment::class.java)
            if (existingDocBlock != null
                && existingDocBlock.isFileLevelDocBlock(currentAfterOpenTag)
                && existingDocBlock.hasFileTag()
            ) {
                return@DrupalCodeStyleTextEditQuickFix emptyList()
            }

            val docBlock = "/**\n * @file\n * TODO: Describe the file.\n *" + "/\n"
            listOf(TextEdit(currentAfterOpenTag, docBlock))
        }

    private fun insertFileTagFix(): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Insert @file tag") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            // Idempotency guard.
            if (docComment.hasFileTag()) {
                return@DrupalCodeStyleTextEditQuickFix emptyList()
            }

            val insertOffset = docComment.fileTagInsertOffset()
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            val hasOtherContent = docComment.hasContentBeyondOpenClose()

            val insertion = if (hasOtherContent) " * @file\n *\n" else " * @file\n"
            listOf(TextEdit(insertOffset, insertion))
        }
}

// -------------------------------------------------------------------------
// PhpFile helpers
// -------------------------------------------------------------------------

private fun PhpFile.isProceduralDrupalFile(): Boolean {
    val ext = name.substringAfterLast('.', "")
    return ext in setOf("module", "install", "theme", "inc", "profile")
}

// -------------------------------------------------------------------------
// PhpDocComment helpers
// -------------------------------------------------------------------------

// Returns true when the docblock starts after the open tag but before any
// other non-whitespace content, i.e. it is a file-level docblock.
private fun PhpDocComment.isFileLevelDocBlock(afterOpenTagOffset: Int): Boolean {
    val fileText = containingFile?.text ?: return false
    val docStart = textRange.startOffset
    if (docStart < afterOpenTagOffset) return false
    // Everything between the open tag and the docblock must be whitespace.
    return fileText.substring(afterOpenTagOffset, docStart).isBlank()
}

// Returns true when the docblock contains a @file tag.
private fun PhpDocComment.hasFileTag(): Boolean =
    drupalDocBlockLines().any { it.tagName() == "file" }

// Returns true when the docblock has content lines beyond its open/close markers.
private fun PhpDocComment.hasContentBeyondOpenClose(): Boolean =
    drupalDocBlockLines().any { line ->
        !line.isOpeningLine() && !line.isClosingLine() && !line.isBlankContent
    }

// Returns the absolute file offset at which " * @file\n" should be inserted
// -- immediately after the opening /** line.
private fun PhpDocComment.fileTagInsertOffset(): Int? {
    val lines = drupalDocBlockLines()
    val openLine = lines.firstOrNull { it.isOpeningLine() } ?: return null
    val absoluteOpenLineEnd = textRange.startOffset + openLine.lineEndOffset
    val fileText = containingFile?.text ?: return null
    return when {
        fileText.getOrNull(absoluteOpenLineEnd) == '\r' && fileText.getOrNull(absoluteOpenLineEnd + 1) == '\n' ->
            absoluteOpenLineEnd + 2
        fileText.getOrNull(absoluteOpenLineEnd) in setOf('\r', '\n') ->
            absoluteOpenLineEnd + 1
        else -> absoluteOpenLineEnd
    }
}
