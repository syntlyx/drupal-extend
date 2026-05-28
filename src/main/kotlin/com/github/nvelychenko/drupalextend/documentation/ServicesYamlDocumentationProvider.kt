package com.github.nvelychenko.drupalextend.documentation

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.documentation.PhpDocumentationProvider
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class ServicesYamlDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return documentationTarget(element, originalElement)?.quickNavigateInfo()
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        return documentationTarget(element, originalElement)?.documentation()
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    private fun documentationTarget(element: PsiElement?, originalElement: PsiElement?): DocumentationTarget? {
        val contextElement = originalElement ?: element ?: return null
        val project = contextElement.project
        if (!Services.isSupportEnabled(project)) return null
        if (!ServicesYaml.isServicesFileName(contextElement.containingFile?.virtualFile?.name)) return null

        ServicesYaml.serviceDefinitionFromKeyElement(contextElement)?.let { serviceDefinition ->
            return DocumentationTarget.Service(serviceDefinition.keyText, serviceDefinition.toSmartPointer())
        }

        val scalar = (contextElement as? YAMLScalar)
            ?: PsiTreeUtil.getParentOfType(contextElement, YAMLScalar::class.java, false)
            ?: return null
        if (ServicesYaml.isKey(scalar)) return null

        classTargetFromScalar(scalar)?.let { classTarget ->
            return DocumentationTarget.PhpClass(classTarget.toSmartPointer())
        }

        serviceIdFromScalar(scalar)?.let { serviceId ->
            return DocumentationTarget.Service(serviceId, Services.definition(project, serviceId)?.toSmartPointer())
        }

        return null
    }

    private fun classTargetFromScalar(scalar: YAMLScalar): PsiElement? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (keyValue.keyText != "class") return null
        if (!keyValue.keyPath.startsWith("services.")) return null

        val classFqn = scalar.textValue.trim().takeIf { it.isNotBlank() } ?: return null
        return PhpIndex.getInstance(scalar.project)
            .getAnyByFQN(classFqn.normalizeServiceClassFqn())
            .firstOrNull()
    }

    private fun serviceIdFromScalar(scalar: YAMLScalar): String? {
        val textValue = scalar.textValue.trim()
        ServicesYaml.serviceIdFromReferenceValue(textValue)?.let { return it }

        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (!ServicesYaml.isPlainServiceIdKeyPath(keyValue.keyPath)) return null

        return textValue.takeIf { it.isNotBlank() }
    }

    private fun YAMLKeyValue.toSmartPointer(): SmartPsiElementPointer<YAMLKeyValue> {
        return SmartPointerManager.createPointer(this)
    }

    private fun PsiElement.toSmartPointer(): SmartPsiElementPointer<PsiElement> {
        return SmartPointerManager.createPointer(this)
    }

    private sealed class DocumentationTarget {
        abstract fun quickNavigateInfo(): String
        abstract fun documentation(): String

        data class Service(
            val serviceId: String,
            val definition: SmartPsiElementPointer<YAMLKeyValue>?,
        ) : DocumentationTarget() {
            override fun quickNavigateInfo(): String {
                return buildString {
                    append(serviceId)
                    serviceClass()?.let { append(" -> ").append(it) }
                }
            }

            override fun documentation(): String {
                return buildDocumentation(
                    definition = "service ${serviceId.escape()}",
                    sections = buildList {
                        serviceClass()?.let { add("Class" to it) }
                        definitionElement()?.containingFile?.virtualFile?.name?.let { add("Declared in" to it) }
                    }
                )
            }

            private fun serviceClass(): String? {
                return definitionElement()?.let { ServicesYaml.serviceClass(it) }?.takeIf { it.isNotBlank() }
            }

            private fun definitionElement(): YAMLKeyValue? {
                return definition?.element
            }
        }

        data class PhpClass(
            val target: SmartPsiElementPointer<PsiElement>,
        ) : DocumentationTarget() {
            override fun quickNavigateInfo(): String {
                val element = target.element ?: return ""
                return PhpDocumentationProvider().getQuickNavigateInfo(element, element) ?: element.text
            }

            override fun documentation(): String {
                val element = target.element ?: return ""
                return PhpDocumentationProvider().generateDoc(element, element)
                    ?: buildDocumentation("class ${element.text.escape()}", emptyList())
            }
        }

        protected fun buildDocumentation(
            definition: String,
            sections: List<Pair<String, String>>,
        ): String {
            return buildString {
                append(DocumentationMarkup.DEFINITION_START)
                append(definition)
                append(DocumentationMarkup.DEFINITION_END)

                if (sections.isNotEmpty()) {
                    append(DocumentationMarkup.SECTIONS_START)
                    sections.forEach { (name, value) ->
                        append(DocumentationMarkup.SECTION_HEADER_START)
                        append(name.escape())
                        append(DocumentationMarkup.SECTION_SEPARATOR)
                        append(value.escape())
                        append(DocumentationMarkup.SECTION_END)
                    }
                    append(DocumentationMarkup.SECTIONS_END)
                }
            }
        }

        protected fun String.escape(): String {
            return StringUtil.escapeXmlEntities(this)
        }
    }
}
