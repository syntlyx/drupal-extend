package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.PhpImportPlanner
import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.php.lang.psi.elements.MethodReference

internal class InjectDrupalServiceQuickFix(
    private val serviceId: String,
) : LocalQuickFix {
    override fun getFamilyName(): String {
        return "Inject Drupal service into constructor"
    }

    override fun getName(): String {
        return "Inject service '$serviceId' into constructor"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val methodReference = descriptor.psiElement as? MethodReference ?: return
        val serviceCall = DrupalStaticServiceCall.from(project, methodReference) ?: return
        val phpClass = serviceCall.targetClassForInjection() ?: return
        val psiFile = methodReference.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        val classBodyStartOffset = phpClass.classBodyStartOffset() ?: return
        val edits = mutableListOf<TextEdit>()
        val constructor = phpClass.ownConstructor
        val imports = PhpImportPlanner(document.text, phpClass)
        val serviceType = imports.typeName(serviceCall.serviceClass)
        val shouldAddCreateMethod = constructor == null
                && phpClass.findOwnMethodByName("create") == null
                && phpClass.isContainerInjectionTarget()
        val containerType = if (shouldAddCreateMethod) imports.typeName(CONTAINER_INTERFACE) else null

        if (phpClass.findOwnFieldByName(serviceCall.propertyName, false) == null) {
            if (constructor == null) {
                edits += TextEdit(
                    classBodyStartOffset,
                    classBodyStartOffset,
                    serviceCall.newPropertyAndConstructorText(serviceType, containerType)
                )
            } else {
                edits += TextEdit(
                    classBodyStartOffset,
                    classBodyStartOffset,
                    serviceCall.newPropertyText(serviceType)
                )
                constructor.parameterInsertionEdit(serviceCall, serviceType)?.let { edits += it }
                constructor.assignmentInsertionEdit(serviceCall)?.let { edits += it }
            }
        }

        phpClass.findOwnMethodByName("create")
            ?.staticCreateInsertionEdit(serviceCall)
            ?.let { edits += it }
        imports.importsInsertionEdit()?.let { edits += it }

        edits += TextEdit(
            methodReference.textRange.startOffset,
            methodReference.textRange.endOffset,
            $$"$this->$${serviceCall.propertyName}"
        )

        edits.applyTo(document)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
