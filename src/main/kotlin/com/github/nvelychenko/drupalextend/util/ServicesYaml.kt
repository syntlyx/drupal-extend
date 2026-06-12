package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.extensions.parents
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
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

    /**
     * Standard core service tags hoisted so first-time usage completes and validates even when no
     * service in the project carries the tag yet (e.g. a brand-new module). Unioned with the live
     * index keys; intentionally curated rather than exhaustive — unknown tags are only ever
     * surfaced as weak warnings, never errors, because tags may come from not-installed modules.
     */
    val curatedCoreTags = setOf(
        "event_subscriber",
        "access_check",
        "cache.context",
        "cache.bin",
        "cache.bin.memory",
        "breadcrumb_builder",
        "theme_negotiator",
        "route_enhancer",
        "route_filter",
        "path_processor_inbound",
        "path_processor_outbound",
        "paramconverter",
        "middleware",
        "http_client_middleware",
        "authentication_provider",
        "page_cache_request_policy",
        "page_cache_response_policy",
        "dynamic_page_cache_request_policy",
        "dynamic_page_cache_response_policy",
        "plugin_manager_cache_clear",
        "module_install.uninstall_validator",
        "config.factory.override",
        "service_collector",
        "service_id_collector",
        "persist",
        "needs_destruction",
        "backend_overridable",
        "twig.extension",
        "twig.loader",
        "drush.command",
    )

    private val taggedArgumentTagNames = setOf("!tagged_iterator", "!tagged_locator")

    fun isServicesFileName(fileName: String?): Boolean {
        return fileName?.endsWith(".services.yml") == true
    }

    /**
     * True when [scalar] is the value that names a service tag under a `tags:` block — either the
     * `name:` value of `tags: [{ name: '<here>' }]` or the bare scalar of `tags: ['<here>']`.
     */
    fun isTagNameValuePosition(scalar: YAMLScalar): Boolean {
        if (isKey(scalar)) return false

        val directParent = scalar.parent as? YAMLKeyValue
        if (directParent != null && directParent.value == scalar) {
            return directParent.keyText == "name"
                    && directParent.keyPath.startsWith("services.")
                    && directParent.keyPath.endsWith(".tags.name")
        }

        // Bare scalar form: the scalar is a direct sequence item value of the `tags` sequence.
        val sequenceItem = scalar.parent as? YAMLSequenceItem ?: return false
        if (sequenceItem.value != scalar) return false
        val tagsKeyValue = sequenceItem.parents
            .filterIsInstance<YAMLKeyValue>()
            .firstOrNull() ?: return false
        return tagsKeyValue.keyText == "tags" && tagsKeyValue.keyPath.startsWith("services.")
    }

    /**
     * The `!tagged_iterator` / `!tagged_locator` tag name [scalar] refers to, or `null` when the
     * scalar is not in such a position. Handles both the scalar form (`- !tagged_iterator tag.name`)
     * and the mapping form (`- !tagged_iterator { tag: tag.name }`).
     */
    fun taggedArgumentTagName(scalar: YAMLScalar): String? {
        if (isKey(scalar)) return null
        if (!isTaggedArgumentTagNamePosition(scalar)) return null

        return scalar.textValue.trim().takeIf { it.isNotBlank() && !it.contains("%") }
    }

    /**
     * The tag-name scalar [element] belongs to, in *any* tag position — a declaration under
     * `tags:` or a consumer (`!tagged_iterator` / `service_collector`) — walking up from a caret
     * leaf when needed. Null outside a `*.services.yml` file or a tag position. The usage
     * machinery's entry point: symbol declaration, usage-target detection, find-usages handler,
     * and references search all key off it, so clicking a tag name anywhere shows every site.
     */
    fun tagPositionScalar(element: PsiElement): YAMLScalar? {
        if (!isServicesFileName(element.containingFile?.virtualFile?.name)) return null

        val scalar = element as? YAMLScalar
            ?: PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java, false)
            ?: return null

        return scalar.takeIf { tagNameAtPosition(it) != null }
    }

    /** The tag name when [scalar] is any tag position (declaration or consumer), else null. */
    fun tagNameAtPosition(scalar: YAMLScalar): String? {
        if (isTagNameValuePosition(scalar)) {
            return scalar.textValue.trim().takeIf { it.isNotBlank() && !it.contains("%") }
        }

        return consumedTagName(scalar)
    }

    /**
     * The tag a *consumer* position [scalar] collects, in either consumer form: a
     * `!tagged_iterator` / `!tagged_locator` argument, or the `tag:` value of a
     * `{ name: service_collector, tag: '<here>' }` collector tag (the core pattern behind
     * service_collector / service_id_collector).
     */
    fun consumedTagName(scalar: YAMLScalar): String? {
        return taggedArgumentTagName(scalar) ?: serviceCollectorCollectedTagName(scalar)
    }

    private val serviceCollectorTagNames = setOf("service_collector", "service_id_collector")

    private fun serviceCollectorCollectedTagName(scalar: YAMLScalar): String? {
        val tagKeyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (tagKeyValue.value != scalar || tagKeyValue.keyText != "tag") return null

        val mapping = tagKeyValue.parent as? YAMLMapping ?: return null
        val collectorName = (mapping.keyValues.firstOrNull { it.keyText == "name" }?.value as? YAMLScalar)
            ?.textValue?.trim()
        if (collectorName !in serviceCollectorTagNames) return null

        val sequenceItem = mapping.parent as? YAMLSequenceItem ?: return null
        val tagsKeyValue = sequenceItem.parents
            .filterIsInstance<YAMLKeyValue>()
            .firstOrNull() ?: return null
        if (tagsKeyValue.keyText != "tags" || !tagsKeyValue.keyPath.startsWith("services.")) return null

        return scalar.textValue.trim().takeIf { it.isNotBlank() && !it.contains("%") }
    }

    private fun isTaggedArgumentTagNamePosition(scalar: YAMLScalar): Boolean {
        // Scalar form: the scalar itself carries the custom YAML tag.
        if (scalar.hasTaggedArgumentTag()) return true

        // Mapping form: the scalar is the `tag:` value inside a mapping carrying the custom tag.
        val tagKeyValue = scalar.parent as? YAMLKeyValue ?: return false
        if (tagKeyValue.value != scalar || tagKeyValue.keyText != "tag") return false

        val mapping = tagKeyValue.parent as? YAMLMapping ?: return false
        return (mapping as YAMLValue).hasTaggedArgumentTag()
    }

    private fun YAMLValue.hasTaggedArgumentTag(): Boolean {
        val tagText = tag?.text?.trim() ?: return false
        return taggedArgumentTagNames.contains(tagText)
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

    fun scalarTextValueRange(scalar: YAMLScalar, textValue: String): TextRange {
        val valueStart = scalar.text.indexOf(textValue).takeIf { it >= 0 } ?: 0
        return TextRange(valueStart, valueStart + textValue.length)
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

    fun serviceFactoryMethods(yamlFile: YAMLFile?): List<ServiceFactoryMethod> {
        val servicesMapping = servicesMapping(PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java))
            ?: return emptyList()

        return servicesMapping.keyValues
            .mapNotNull(::serviceFactoryMethod)
    }

    fun serviceFactoryMethod(serviceDefinition: YAMLKeyValue): ServiceFactoryMethod? {
        if (!isServiceDefinition(serviceDefinition)) return null

        val serviceMapping = serviceDefinition.value as? YAMLMapping ?: return null
        val factoryValue = serviceMapping.keyValues.firstOrNull { it.keyText == "factory" }?.value
            ?: return null

        return when (factoryValue) {
            is YAMLSequence -> factorySequenceMethod(factoryValue)
            is YAMLScalar -> factoryScalarMethod(factoryValue)
            else -> null
        }
    }

    fun serviceFactoryMethodFromMethodScalar(scalar: YAMLScalar): ServiceFactoryMethod? {
        return factorySequenceMethodFromScalar(scalar)
            ?: factoryScalarMethod(scalar)
    }

    private fun factorySequenceMethodFromScalar(scalar: YAMLScalar): ServiceFactoryMethod? {
        val sequenceItem = scalar.parent as? YAMLSequenceItem ?: return null
        if (sequenceItem.value != scalar) return null

        val sequence = sequenceItem.parent as? YAMLSequence ?: return null
        return factorySequenceMethod(sequence, scalar)
    }

    private fun factorySequenceMethod(
        sequence: YAMLSequence,
        expectedMethodScalar: YAMLScalar? = null
    ): ServiceFactoryMethod? {
        val factoryKey = sequence.parent as? YAMLKeyValue ?: return null
        if (!isServiceFactoryKey(factoryKey, sequence)) return null

        val scalars = sequence.items
            .mapNotNull { it.value as? YAMLScalar }
        if (scalars.size < 2) return null

        val methodScalar = scalars[1]
        if (expectedMethodScalar != null && methodScalar != expectedMethodScalar) return null

        val ownerValue = scalars[0].textValue.trim()
        val methodName = methodScalar.textValue.trim()
        if (methodName.isBlank() || methodName.contains("%")) return null

        val ownerServiceId = serviceIdFromReferenceValue(ownerValue)
        val ownerClassFqn = if (ownerServiceId == null) ownerValue.toFactoryClassFqn() else null
        if (ownerServiceId == null && ownerClassFqn == null) return null

        return ServiceFactoryMethod(
            ownerServiceId = ownerServiceId,
            ownerClassFqn = ownerClassFqn,
            methodName = methodName,
            methodTextRange = scalarTextValueRange(methodScalar, methodName),
        )
    }

    private fun factoryScalarMethod(scalar: YAMLScalar): ServiceFactoryMethod? {
        val factoryKey = scalar.parent as? YAMLKeyValue ?: return null
        if (!isServiceFactoryKey(factoryKey, scalar)) return null

        val textValue = scalar.textValue.trim()
        val classFqn = textValue.substringBefore("::").toFactoryClassFqn() ?: return null
        val methodName = textValue.substringAfter("::", "").trim()
        if (methodName.isBlank() || methodName.contains("%")) return null

        val valueRange = scalarTextValueRange(scalar, textValue)
        val methodStart = textValue.indexOf("::") + 2

        return ServiceFactoryMethod(
            ownerServiceId = null,
            ownerClassFqn = classFqn,
            methodName = methodName,
            methodTextRange = TextRange(
                valueRange.startOffset + methodStart,
                valueRange.startOffset + methodStart + methodName.length
            ),
        )
    }

    private fun isServiceFactoryKey(keyValue: YAMLKeyValue, value: YAMLValue): Boolean {
        return keyValue.keyText == "factory"
                && keyValue.value == value
                && keyValue.keyPath.startsWith("services.")
    }

    private fun String.toFactoryClassFqn(): String? {
        val classFqn = trim().substringBefore("::").trim()
        if (classFqn.isBlank()) return null
        if (classFqn.startsWith("@") || classFqn.contains("%")) return null

        return classFqn
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

    data class ServiceFactoryMethod(
        val ownerServiceId: String?,
        val ownerClassFqn: String?,
        val methodName: String,
        val methodTextRange: TextRange,
    )
}
