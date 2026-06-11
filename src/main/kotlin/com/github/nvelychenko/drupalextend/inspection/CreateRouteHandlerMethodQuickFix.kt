package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Generates a stub for a route handler method that the routes YAML references on an existing —
 * but methodless — handler class (`_controller`, `_form` callbacks, `_title_callback`,
 * `_custom_access`). The fix writes into the PHP class file, not the YAML it is attached to, so
 * it resolves the class fresh, edits its [com.intellij.openapi.editor.Document], and commits.
 *
 * Only offered for classes living in writable project content — never core/vendor, where stubbing
 * a method would be both pointless and (read-only) impossible.
 */
internal class CreateRouteHandlerMethodQuickFix(
    private val classFqn: String,
    private val methodName: String,
    private val returnsString: Boolean,
) : LocalQuickFix {
    override fun getFamilyName(): String {
        return "Create route handler method"
    }

    override fun getName(): String {
        return "Create method '$methodName'"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val phpClass = resolveWritableProjectClass(project) ?: return
        // Re-check after the highlight was computed: the method may have appeared meanwhile, in
        // which case the fix must be a no-op rather than emit a duplicate.
        if (phpClass.findOwnMethodByName(methodName) != null) return

        val psiFile = phpClass.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        // Insert before the class's closing `}`. classBodyStartOffset() guards against attribute
        // braces; the closing brace is the class node's last char, so endOffset - 1 is the spot
        // right before it even when the body is empty.
        val bodyStart = phpClass.classBodyStartOffset() ?: return
        val closingBraceOffset = phpClass.textRange.endOffset - 1
        if (closingBraceOffset < bodyStart) return

        val memberIndent = phpClass.memberIndent()
        val bodyIndent = "$memberIndent$memberIndent"
        val edit = TextEdit(closingBraceOffset, closingBraceOffset, methodStub(memberIndent, bodyIndent))

        listOf(edit).applyTo(document)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private fun methodStub(memberIndent: String, bodyIndent: String): String {
        val (returnType, body, doc) = if (returnsString) {
            Triple("string", "return '';", "TODO: Build the route title.")
        } else {
            Triple("array", "return [];", "TODO: Describe the route handler.")
        }

        return "\n" +
                "$memberIndent/**\n" +
                "$memberIndent * $doc\n" +
                "$memberIndent */\n" +
                "$memberIndent" + "public function $methodName(): $returnType {\n" +
                "$bodyIndent" + "// TODO: Implement the route handler.\n" +
                "$bodyIndent$body\n" +
                "$memberIndent}\n"
    }

    private fun resolveWritableProjectClass(project: Project): PhpClass? {
        val phpClass = PhpIndex.getInstance(project)
            .getClassesByFQN(classFqn)
            .firstOrNull()
            ?: return null
        val virtualFile = phpClass.containingFile?.virtualFile ?: return null
        if (!virtualFile.isWritable) return null
        if (!ProjectFileIndex.getInstance(project).isInContent(virtualFile)) return null

        return phpClass
    }
}
