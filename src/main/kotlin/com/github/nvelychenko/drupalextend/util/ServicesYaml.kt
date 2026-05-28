package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.extensions.parents
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

object ServicesYaml {
    private val serviceReferenceKeyPathParts = setOf(
        "arguments",
        "argument",
        "calls",
        "call",
        "factory",
        "configurator"
    )

    private val serviceIdKeyPathParts = setOf(
        "parent",
        "decorates",
        "alias"
    )

    fun isServicesFileName(fileName: String?): Boolean {
        return fileName?.endsWith(".services.yml") == true
    }

    fun isServiceReferenceKeyPath(keyPath: String): Boolean {
        return serviceReferenceKeyPathParts.any { keyPath.contains(".$it") || keyPath.endsWith(".$it") }
    }

    fun isPlainServiceIdKeyPath(keyPath: String): Boolean {
        return serviceIdKeyPathParts.any { keyPath.endsWith(".$it") }
    }

    fun isClassReferenceKeyPath(keyPath: String): Boolean {
        return keyPath.endsWith(".class")
                || keyPath.endsWith(".factory")
                || keyPath.endsWith(".configurator")
    }

    fun serviceIdFromReferenceValue(textValue: String): String? {
        val trimmedValue = textValue.trim()
        if (!trimmedValue.startsWith("@") || trimmedValue.startsWith("@@")) return null

        val serviceId = trimmedValue
            .removePrefix("@")
            .removePrefix("?")

        if (serviceId.isBlank() || serviceId.startsWith("=")) return null

        return serviceId
    }

    fun referenceValueTextRange(scalar: YAMLScalar, textValue: String, serviceId: String): TextRange {
        val valueStart = scalar.text.indexOf(textValue).takeIf { it >= 0 } ?: 0
        val serviceIdStart = textValue.indexOf(serviceId).takeIf { it >= 0 } ?: 0
        val startOffset = valueStart + serviceIdStart

        return TextRange(startOffset, startOffset + serviceId.length)
    }

    fun isKey(scalar: YAMLScalar): Boolean {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return false
        return keyValue.key == scalar
    }

    fun serviceKeyPath(scalar: YAMLScalar): String? {
        val directParent = scalar.parent as? YAMLKeyValue
        if (directParent != null && directParent.value == scalar) {
            return directParent.keyPath
        }

        return scalar.parents
            .filterIsInstance<YAMLKeyValue>()
            .firstOrNull()
            ?.keyPath
    }

    fun servicesMapping(fileRootMapping: YAMLMapping?): YAMLMapping? {
        return fileRootMapping
            ?.keyValues
            ?.firstOrNull { it.keyText == "services" }
            ?.value as? YAMLMapping
    }

    fun isServiceDefinition(keyValue: YAMLKeyValue): Boolean {
        val parentKeyValue = keyValue.parent?.parent as? YAMLKeyValue ?: return false

        return keyValue.keyText.isNotBlank()
                && !keyValue.keyText.startsWith("_")
                && parentKeyValue.keyText == "services"
    }

    fun serviceClass(keyValue: YAMLKeyValue): String? {
        val serviceMapping = keyValue.value as? YAMLMapping ?: return null
        return (serviceMapping.keyValues.firstOrNull { it.keyText == "class" }?.value as? YAMLScalar)?.textValue
    }

    fun hasServiceOption(keyValue: YAMLKeyValue, optionName: String): Boolean {
        val serviceMapping = keyValue.value as? YAMLMapping ?: return false
        return serviceMapping.keyValues.any { it.keyText == optionName }
    }

    fun serviceArguments(keyValue: YAMLKeyValue): List<ServiceArgument> {
        val serviceMapping = keyValue.value as? YAMLMapping ?: return emptyList()
        val argumentsValue = serviceMapping.keyValues.firstOrNull { it.keyText == "arguments" }?.value
            ?: return emptyList()

        return when (argumentsValue) {
            is YAMLSequence -> argumentsValue.items.mapNotNull { item ->
                item.value?.toServiceArgument()
            }

            else -> listOf(argumentsValue.toServiceArgument())
        }
    }

    private fun YAMLValue.toServiceArgument(): ServiceArgument {
        val scalar = this as? YAMLScalar
        return ServiceArgument(
            value = this,
            textValue = scalar?.textValue?.trim(),
        )
    }

    fun serviceDefinitionFromKeyElement(element: PsiElement): YAMLKeyValue? {
        if (!isServicesFileName(element.containingFile?.virtualFile?.name)) return null

        val keyValue = element as? YAMLKeyValue
            ?: element.parent as? YAMLKeyValue
            ?: PsiTreeUtil.getParentOfType(element, YAMLKeyValue::class.java, false)
            ?: return null
        val keyElement = keyValue.key ?: return null
        if (element != keyValue
            && element != keyElement
            && !PsiTreeUtil.isAncestor(keyElement, element, false)
            && !(element is YAMLScalar && PsiTreeUtil.isAncestor(element, keyElement, false))
        ) return null
        if (!isServiceDefinition(keyValue)) return null

        return keyValue
    }

    data class ServiceArgument(
        val value: YAMLValue,
        val textValue: String?,
    )
}
