package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpModifierList

/**
 * Flags OOP hook methods that Drupal's hook system can never invoke.
 *
 * A method carrying `#[Hook(...)]` (`\Drupal\Core\Hook\Attribute\Hook`) must be a public
 * instance method: the hook collector instantiates the class and calls the method on it, so
 * `protected`/`private` make it unreachable and `static` is unsupported.
 *
 * The fqn is compared as a string so the optional `com.jetbrains.php.drupal` plugin is never
 * required. Per-element work is cheap: non-[Method] elements bail immediately, and the only
 * lookup is the (usually empty) attribute list filtered by fqn.
 */
class DrupalHookMethodVisibilityInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!project.drupalExtendSettings.isEnabled) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val method = element as? Method ?: return
                if (method.getAttributes(HOOK_ATTRIBUTE_FQN).isEmpty()) return

                val modifier = method.modifier
                // `static` is reported on its own keyword; if a method is both static and
                // non-public, both problems are valid and each gets its own report.
                if (modifier.isStatic) {
                    val anchor = method.modifierKeyword(KEYWORD_STATIC) ?: method.nameAnchor()
                    if (anchor != null) {
                        holder.registerProblem(
                            anchor,
                            "Hook method must not be static",
                            ProblemHighlightType.GENERIC_ERROR,
                            RemoveStaticQuickFix()
                        )
                    }
                }

                if (!modifier.isPublic) {
                    val keyword = if (modifier.isPrivate) KEYWORD_PRIVATE else KEYWORD_PROTECTED
                    val anchor = method.modifierKeyword(keyword) ?: method.nameAnchor()
                    if (anchor != null) {
                        holder.registerProblem(
                            anchor,
                            "Hook method must be public",
                            ProblemHighlightType.GENERIC_ERROR,
                            MakePublicQuickFix()
                        )
                    }
                }
            }
        }
    }

    /**
     * Replaces the current visibility keyword (`protected`/`private`) with `public`. Idempotent:
     * a method that is already public has no such keyword, so the fix becomes a no-op.
     */
    private class MakePublicQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Make hook method public"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val method = descriptor.psiElement?.parentMethod() ?: return
            if (method.modifier.isPublic) return
            val keyword = method.modifierKeyword(KEYWORD_PROTECTED)
                ?: method.modifierKeyword(KEYWORD_PRIVATE)
                ?: return
            val psiFile = method.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

            listOf(
                TextEdit(keyword.textRange.startOffset, keyword.textRange.endOffset, KEYWORD_PUBLIC)
            ).applyTo(document)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    /**
     * Removes the `static` keyword and the single whitespace that follows it. Idempotent: a
     * non-static method has no `static` keyword, so the fix becomes a no-op.
     */
    private class RemoveStaticQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Remove static from hook method"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val method = descriptor.psiElement?.parentMethod() ?: return
            if (!method.modifier.isStatic) return
            val keyword = method.modifierKeyword(KEYWORD_STATIC) ?: return
            val psiFile = method.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

            // Drop the keyword plus a trailing space so `static public` doesn't leave a double
            // space; the closing brace position and the rest of the header are untouched.
            val end = keyword.textRange.endOffset
            val removeEnd = if (document.charsSequence.getOrNull(end) == ' ') end + 1 else end

            listOf(
                TextEdit(keyword.textRange.startOffset, removeEnd, "")
            ).applyTo(document)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    private companion object {
        const val HOOK_ATTRIBUTE_FQN = "\\Drupal\\Core\\Hook\\Attribute\\Hook"
        const val KEYWORD_PUBLIC = "public"
        const val KEYWORD_PROTECTED = "protected"
        const val KEYWORD_PRIVATE = "private"
        const val KEYWORD_STATIC = "static"
    }
}

private fun PsiElement.parentMethod(): Method? =
    this as? Method ?: PsiTreeUtil.getParentOfType(this, Method::class.java, false)

/**
 * Finds the leaf node for a modifier keyword by matching its text inside the method's
 * [PhpModifierList], so it works regardless of internal token-type field names. Whitespace and
 * comment leaves are skipped; a `getX()` method named like a keyword is never matched because
 * we only look inside the modifier list, not the method name.
 */
private fun Method.modifierKeyword(keyword: String): PsiElement? {
    val modifierList = PsiTreeUtil.getChildOfType(this, PhpModifierList::class.java) ?: return null
    var child = modifierList.firstChild
    while (child != null) {
        if (child.text.equals(keyword, ignoreCase = true)) return child
        child = child.nextSibling
    }
    return null
}

/** Fallback highlight anchor when the modifier keyword leaf can't be located. */
private fun Method.nameAnchor(): PsiElement? = nameIdentifier ?: nameNode?.psi
