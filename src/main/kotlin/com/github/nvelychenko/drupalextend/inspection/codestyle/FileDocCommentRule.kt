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

    override fun isEnabled(settings: DrupalExtendSettings): Boolean =
        settings.isCodeStyleFileDocCommentEnabled

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val file = element as? PhpFile ?: return
        if (!file.isProceduralDrupalFile()) return

        val text = file.text ?: return
        val openTagMatch = PHP_OPEN_TAG_LINE.find(text) ?: return
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

            listOfNotNull(file.fileDocCommentEdit())
        }

    private fun insertFileTagFix(): DrupalCodeStyleTextEditQuickFix =
        DrupalCodeStyleTextEditQuickFix("Insert @file tag") { _, descriptor ->
            val docComment = descriptor.psiElement as? PhpDocComment
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()

            listOfNotNull(docComment.fileTagInsertEdit())
        }
}

/**
 * The single edit that brings the file to a valid `@file` state, or null when nothing is
 * needed. Used by the quick fixes and by the file-wide "Fix Drupal code style" action.
 */
internal fun PhpFile.fileDocCommentEdit(): TextEdit? {
    if (!isProceduralDrupalFile()) return null

    val text = text ?: return null
    val openTagMatch = PHP_OPEN_TAG_LINE.find(text) ?: return null
    val afterOpenTagOffset = openTagMatch.range.last + 1

    val firstDocBlock = PsiTreeUtil.findChildOfType(this, PhpDocComment::class.java)
    if (firstDocBlock != null && firstDocBlock.isFileLevelDocBlock(afterOpenTagOffset)) {
        // A top-of-file docblock exists: only the @file tag may be missing.
        return firstDocBlock.fileTagInsertEdit()
    }

    // No docblock at the top of the file. Nothing to do for an otherwise empty file.
    val contentAfterTag = text.substring(afterOpenTagOffset)
    if (contentAfterTag.isBlank()) return null

    // Keep exactly one blank line between the docblock and the following content.
    val separator = if (contentAfterTag.startsWith("\n") || contentAfterTag.startsWith("\r")) "" else "\n"
    val docBlock = "/**\n * @file\n * ${fileDocDescription()}\n *" + "/\n" + separator

    return TextEdit(afterOpenTagOffset, docBlock)
}

/** `@file`-tag insertion into an existing top-of-file docblock, or null when already present. */
internal fun PhpDocComment.fileTagInsertEdit(): TextEdit? {
    if (hasFileTag()) return null

    val insertOffset = fileTagInsertOffset() ?: return null
    val insertion = if (hasContentBeyondOpenClose()) " * @file\n *\n" else " * @file\n"

    return TextEdit(insertOffset, insertion)
}

/**
 * A Drupal-core-convention description derived from the file name:
 * `nll_core.install` → "Install, update and uninstall functions for the Nll Core module."
 */
private fun PhpFile.fileDocDescription(): String {
    val humanized = name.substringBefore('.')
        .split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        .ifBlank { "this" }

    return when (name.substringAfterLast('.', "")) {
        "install" -> "Install, update and uninstall functions for the $humanized module."
        "module" -> "Hook implementations for the $humanized module."
        "theme" -> "Functions to support theming in the $humanized theme."
        "profile" -> "Enables modules and site configuration for a $humanized site installation."
        else -> "Helper functions for the $humanized module."
    }
}

private val PHP_OPEN_TAG_LINE = Regex("""^<\?php[ \t]*(?:\r?\n|\r)?""")

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
