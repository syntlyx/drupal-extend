package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.PhpImportPlanner
import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.github.nvelychenko.drupalextend.php.edit.escapePhpSingleQuoted
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
        val anchorOffset = phpClass.serviceInjectionAnchorOffset() ?: return
        val edits = mutableListOf<TextEdit>()
        val constructor = phpClass.ownConstructor
        val imports = PhpImportPlanner(document.text, phpClass)
        val serviceType = imports.typeName(serviceCall.serviceClass)
        val memberIndent = phpClass.memberIndent()
        val bodyIndent = "$memberIndent$memberIndent"
        // A class served by AutowireTrait's create() (directly or via a base class like
        // ControllerBase in Drupal 10.2+) must NOT get a create() override — that would replace
        // working autowiring. The new constructor parameter carries an #[Autowire] attribute
        // instead, so the trait resolves it by service id rather than by type.
        val hasOwnCreateMethod = phpClass.findOwnMethodByName("create") != null
        val autowiredCreate = !hasOwnCreateMethod && phpClass.createComesFromAutowireTrait()
        // Generate a create() override whenever the class is an injection target and has no own
        // create(): without one, an added required constructor parameter has nothing supplying it.
        val shouldAddCreateMethod = !hasOwnCreateMethod
                && !autowiredCreate
                && phpClass.isContainerInjectionTarget()
        val parameterAttributePrefix = if (autowiredCreate) {
            "#[${imports.typeName(AUTOWIRE_ATTRIBUTE)}(service: '${serviceCall.serviceId.escapePhpSingleQuoted()}')] "
        } else {
            ""
        }
        // The no-constructor path folds create() into the property+constructor text; an existing
        // constructor needs create() emitted as a standalone member.
        val createTypeForNewConstructor = if (shouldAddCreateMethod && constructor == null) {
            imports.typeName(CONTAINER_INTERFACE)
        } else {
            null
        }

        val accessMemberName = serviceCall.propertyName
        if (phpClass.findOwnFieldByName(serviceCall.propertyName, false) == null) {
            if (constructor == null) {
                edits += TextEdit(
                    anchorOffset,
                    anchorOffset,
                    serviceCall.newPropertyAndConstructorText(
                        serviceType,
                        createTypeForNewConstructor,
                        memberIndent,
                        bodyIndent,
                        parameterAttributePrefix,
                    )
                )
            } else if (constructor.usesConstructorPromotion()) {
                // Promoted property name == camelCase propertyName, so accessMemberName stays as-is.
                constructor.parameterInsertionEdit(serviceCall, serviceType, promoted = true, parameterAttributePrefix)
                    ?.let { edits += it }
            } else {
                edits += TextEdit(
                    anchorOffset,
                    anchorOffset,
                    serviceCall.newPropertyText(serviceType, memberIndent)
                )
                constructor.parameterInsertionEdit(serviceCall, serviceType, promoted = false, parameterAttributePrefix)
                    ?.let { edits += it }
                constructor.assignmentInsertionEdit(serviceCall, constructor.bodyStatementIndent())?.let { edits += it }
            }
        }

        if (shouldAddCreateMethod && constructor != null) {
            // Existing constructor + no own create(): emit a create() override that supplies the new
            // service through the container and leaves clearly-marked placeholders for the existing
            // constructor arguments (we can't infer their container source). Place it right after the
            // constructor — the conventional spot and an unambiguous offset.
            val containerType = imports.typeName(CONTAINER_INTERFACE)
            val existingParameterNames = constructor.parameters.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }
            edits += TextEdit(
                constructor.textRange.endOffset,
                constructor.textRange.endOffset,
                serviceCall.newCreateMethodText(containerType, memberIndent, bodyIndent, existingParameterNames)
            )
        }

        phpClass.findOwnMethodByName("create")
            ?.staticCreateInsertionEdit(serviceCall)
            ?.let { edits += it }
        imports.importsInsertionEdit()?.let { edits += it }

        edits += TextEdit(
            methodReference.textRange.startOffset,
            methodReference.textRange.endOffset,
            $$"$this->$$accessMemberName"
        )

        edits.applyTo(document)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
