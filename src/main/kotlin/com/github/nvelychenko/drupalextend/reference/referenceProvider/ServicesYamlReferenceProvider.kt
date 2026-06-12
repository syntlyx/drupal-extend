package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceClassReference
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceMethodReference
import com.github.nvelychenko.drupalextend.reference.referenceType.ServiceReference
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class ServicesYamlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val project = element.project

        if (!Services.isSupportEnabled(project)) return PsiReference.EMPTY_ARRAY

        if (!ServicesYaml.isServicesFileName(element.containingFile?.virtualFile?.name)) return PsiReference.EMPTY_ARRAY

        val scalar = element as? YAMLScalar
            ?: PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java, false)
            ?: return PsiReference.EMPTY_ARRAY

        if (ServicesYaml.isKey(scalar)) {
            return PsiReference.EMPTY_ARRAY
        }

        // Tag-name positions intentionally carry no reference: a self-resolving reference sends
        // Ctrl+Click into a dead in-place navigation instead of the Show Usages branch driven by
        // ServiceTagSymbolDeclarationProvider.
        if (ServicesYaml.tagNameAtPosition(scalar) != null) return PsiReference.EMPTY_ARRAY

        getServiceReference(scalar)?.let { return arrayOf(it) }
        getPlainServiceIdReference(scalar)?.let { return arrayOf(it) }
        getFactoryMethodReference(scalar)?.let { return arrayOf(it) }
        getClassReference(scalar)?.let { return arrayOf(it) }

        return PsiReference.EMPTY_ARRAY
    }

    private fun getServiceReference(scalar: YAMLScalar): PsiReference? {
        val textValue = scalar.textValue.trim()
        val serviceId = ServicesYaml.serviceIdFromReferenceValue(textValue) ?: return null

        return ServiceReference(scalar, serviceId, ServicesYaml.referenceValueTextRange(scalar, textValue, serviceId))
    }

    private fun getPlainServiceIdReference(scalar: YAMLScalar): PsiReference? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (!ServicesYaml.isPlainServiceIdKeyPath(keyValue.keyPath)) return null

        val serviceId = scalar.textValue.trim()
        if (serviceId.isBlank()) return null

        return ServiceReference(scalar, serviceId)
    }

    private fun getFactoryMethodReference(scalar: YAMLScalar): PsiReference? {
        val factoryMethod = ServicesYaml.serviceFactoryMethodFromMethodScalar(scalar) ?: return null

        return ServiceMethodReference(scalar, factoryMethod)
    }

    private fun getClassReference(scalar: YAMLScalar): PsiReference? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (keyValue.keyText != "class") return null
        if (!keyValue.keyPath.startsWith("services.")) return null

        val classFqn = scalar.textValue.trim()
        if (classFqn.isBlank()) return null

        return ServiceClassReference(scalar, classFqn)
    }
}
