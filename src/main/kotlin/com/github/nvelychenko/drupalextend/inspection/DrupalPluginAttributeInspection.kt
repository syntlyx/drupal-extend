package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.inspection.pluginattribute.DrupalPluginAttributeConversion
import com.github.nvelychenko.drupalextend.inspection.pluginattribute.DrupalPluginAttributeConverter
import com.github.nvelychenko.drupalextend.php.edit.PhpImportPlanner
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
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.PhpClass

class DrupalPluginAttributeInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!project.drupalExtendSettings.isEnabled) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val phpClass = element as? PhpClass ?: return
                val docComment = phpClass.docComment ?: return
                val conversion = DrupalPluginAttributeConverter.findFirst(docComment.text, project) ?: return
                if (phpClass.attributes.any { it.fqn == conversion.spec.attributeFqn }) return

                holder.registerProblem(
                    docComment,
                    "${conversion.spec.annotationName} annotation can be converted to a PHP attribute",
                    ProblemHighlightType.WEAK_WARNING,
                    ConvertDrupalPluginAnnotationQuickFix(conversion.spec.annotationName)
                )
            }
        }
    }

    private class ConvertDrupalPluginAnnotationQuickFix(
        private val annotationName: String,
    ) : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Convert Drupal plugin annotation to attribute"
        }

        override fun getName(): String {
            return "Convert @$annotationName annotation to PHP attribute"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val problemElement = descriptor.startElement ?: descriptor.psiElement
            val docComment = problemElement as? PhpDocComment
                ?: PsiTreeUtil.getParentOfType(problemElement, PhpDocComment::class.java, false)
                ?: findDocCommentForProblemElement(problemElement)
                ?: return
            val phpClass = findClassForDocComment(docComment) ?: return
            val conversion = DrupalPluginAttributeConverter.parse(docComment.text, annotationName, project) ?: return
            applyConversion(project, docComment, phpClass, conversion)
        }

        private fun applyConversion(
            project: Project,
            docComment: PhpDocComment,
            phpClass: PhpClass,
            conversion: DrupalPluginAttributeConversion,
        ) {
            val psiFile = phpClass.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
            val imports = PhpImportPlanner(document.text, phpClass)
            val attributeText = conversion.attributeText(imports) ?: return
            val docCommentStart = docComment.textRange.startOffset
            val removalStart = docCommentStart + conversion.removalRange.startOffset
            val removalEnd = document.text.docCommentRemovalEnd(
                docCommentStart + conversion.removalRange.endOffset,
                removesEntireDocComment = conversion.removalRange.startOffset == 0 &&
                    conversion.removalRange.endOffset == docComment.textLength
            )
            val edits = mutableListOf(
                TextEdit(
                    removalStart,
                    removalEnd,
                    ""
                ),
                TextEdit(
                    phpClass.textRange.startOffset,
                    "$attributeText\n"
                )
            )

            imports.importsInsertionEdit()?.let { edits += it }
            edits.applyTo(document)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        private fun findDocCommentForProblemElement(element: PsiElement): PhpDocComment? {
            val elementRange = element.textRange ?: return null
            return PsiTreeUtil.findChildrenOfType(element.containingFile, PhpClass::class.java)
                .mapNotNull { it.docComment }
                .firstOrNull { docComment ->
                    val docRange = docComment.textRange
                    docRange.startOffset <= elementRange.endOffset && docRange.endOffset >= elementRange.startOffset
                }
        }

        private fun findClassForDocComment(docComment: PhpDocComment): PhpClass? {
            return PsiTreeUtil.findChildrenOfType(docComment.containingFile, PhpClass::class.java)
                .firstOrNull { it.docComment == docComment }
        }
    }
}

private fun String.docCommentRemovalEnd(offset: Int, removesEntireDocComment: Boolean): Int {
    if (!removesEntireDocComment) return offset

    return when {
        getOrNull(offset) == '\r' && getOrNull(offset + 1) == '\n' -> offset + 2
        getOrNull(offset) == '\n' || getOrNull(offset) == '\r' -> offset + 1
        else -> offset
    }
}
