package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.extensions.parents
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

object RoutingYaml {
    private val routeReferenceKeyNames = setOf(
        "route_name",
        "base_route",
        "appears_on"
    )

    private val defaultsHandlerKeyNames = setOf(
        "_controller",
        "_form",
        "_title_callback",
    )

    private val requirementsHandlerKeyNames = setOf(
        "_custom_access",
    )

    fun isRoutingFileName(fileName: String?): Boolean {
        return fileName?.endsWith(".routing.yml") == true
    }

    fun isMenuLinksFileName(fileName: String?): Boolean {
        return fileName?.endsWith(".links.menu.yml") == true
    }

    fun isLinksFileName(fileName: String?): Boolean {
        return isMenuLinksFileName(fileName)
                || fileName?.endsWith(".links.task.yml") == true
                || fileName?.endsWith(".links.action.yml") == true
                || fileName?.endsWith(".links.contextual.yml") == true
    }

    fun isRouteReferenceFileName(fileName: String?): Boolean {
        return isRoutingFileName(fileName) || isLinksFileName(fileName)
    }

    fun isRouteReferenceKeyPath(keyPath: String): Boolean {
        return routeReferenceKeyNames.any { keyPath.endsWith(".$it") || keyPath == it }
    }

    fun isRouteHandlerKeyPath(keyPath: String): Boolean {
        return defaultsHandlerKeyNames.any { keyPath.endsWith(".defaults.$it") }
                || requirementsHandlerKeyNames.any { keyPath.endsWith(".requirements.$it") }
    }

    fun isMenuLinkParentKeyPath(keyPath: String): Boolean {
        return keyPath == "parent" || keyPath.endsWith(".parent")
    }

    fun isLinkParentKeyPath(keyPath: String): Boolean {
        return isMenuLinkParentKeyPath(keyPath)
                || keyPath == "parent_id"
                || keyPath.endsWith(".parent_id")
    }

    fun isKey(scalar: YAMLScalar): Boolean {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return false
        return keyValue.key == scalar
    }

    fun scalarKeyPath(scalar: YAMLScalar): String? {
        val directParent = scalar.parent as? YAMLKeyValue
        if (directParent != null && directParent.value == scalar) {
            return directParent.keyPath
        }

        return scalar.parents
            .filterIsInstance<YAMLKeyValue>()
            .firstOrNull()
            ?.keyPath
    }

    fun routeDefinitions(yamlFile: YAMLFile): List<YAMLKeyValue> {
        val rootMapping = PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java) ?: return emptyList()
        return rootMapping.keyValues.filter { isRouteDefinition(it) }
    }

    fun isRouteDefinition(keyValue: YAMLKeyValue): Boolean {
        val parentMapping = keyValue.parent as? YAMLMapping ?: return false
        if (parentMapping.parent !is YAMLDocument) return false

        return keyValue.keyText.isNotBlank() && keyValue.value is YAMLMapping
    }

    fun routeDefinition(yamlFile: YAMLFile, routeName: String): YAMLKeyValue? {
        return routeDefinitions(yamlFile).firstOrNull { it.keyText == routeName }
    }

    fun routeDefinitionFromKeyElement(element: PsiElement): YAMLKeyValue? {
        if (!isRoutingFileName(element.containingFile?.virtualFile?.name)) return null

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
        if (!isRouteDefinition(keyValue)) return null

        return keyValue
    }

    fun routePath(keyValue: YAMLKeyValue): String {
        val routeMapping = keyValue.value as? YAMLMapping ?: return ""
        return (routeMapping.keyValues.firstOrNull { it.keyText == "path" }?.value as? YAMLScalar)
            ?.textValue
            ?.trim()
            .orEmpty()
    }

    fun routePathParameters(keyValue: YAMLKeyValue): List<String> {
        return routePathParameters(routePath(keyValue))
    }

    fun routePathParameters(path: String): List<String> {
        return Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}")
            .findAll(path)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("_") }
            .distinct()
            .toList()
    }

    fun routeHandler(keyValue: YAMLKeyValue): RouteHandler? {
        return routeHandlers(keyValue).firstOrNull()
    }

    fun routeHandlers(keyValue: YAMLKeyValue): List<RouteHandler> {
        val routeMapping = keyValue.value as? YAMLMapping ?: return emptyList()

        return handlerValues(routeMapping, "defaults", defaultsHandlerKeyNames) +
                handlerValues(routeMapping, "requirements", requirementsHandlerKeyNames)
    }

    private fun handlerValues(
        routeMapping: YAMLMapping,
        sectionName: String,
        handlerKeyNames: Set<String>,
    ): List<RouteHandler> {
        val sectionMapping = routeMapping.keyValues
            .firstOrNull { it.keyText == sectionName }
            ?.value as? YAMLMapping
            ?: return emptyList()

        return sectionMapping.keyValues
            .filter { it.keyText in handlerKeyNames }
            .mapNotNull { (it.value as? YAMLScalar)?.textValue?.let(::routeHandlerFromText) }
    }

    fun routeHandlerFromText(textValue: String): RouteHandler? {
        val handlerText = textValue.trim().removePrefix("\\")
        if (handlerText.isBlank()) return null
        if (!handlerText.contains("\\")) return null

        val className = handlerText.substringBefore("::").trim().removePrefix("\\")
        if (className.isBlank()) return null

        val methodName = handlerText
            .substringAfter("::", "")
            .trim()
            .takeIf { it.isNotBlank() }

        return RouteHandler(className, methodName)
    }

    fun handlerValueTextRange(scalar: YAMLScalar, textValue: String, handler: RouteHandler): TextRange {
        val valueStart = scalar.text.indexOf(textValue).takeIf { it >= 0 } ?: 0
        val handlerStart = textValue.indexOf(handler.className).takeIf { it >= 0 } ?: 0
        val handlerText = handler.methodName
            ?.let { "${handler.className}::$it" }
            ?: handler.className
        return TextRange(valueStart + handlerStart, valueStart + handlerStart + handlerText.length)
    }

    fun linkDefinitions(yamlFile: YAMLFile): List<YAMLKeyValue> {
        val rootMapping = PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java) ?: return emptyList()
        return rootMapping.keyValues.filter { isLinkDefinition(it) }
    }

    fun isLinkDefinition(keyValue: YAMLKeyValue): Boolean {
        val parentMapping = keyValue.parent as? YAMLMapping ?: return false
        if (parentMapping.parent !is YAMLDocument) return false

        return keyValue.keyText.isNotBlank() && keyValue.value is YAMLMapping
    }

    fun linkDefinition(yamlFile: YAMLFile, linkId: String): YAMLKeyValue? {
        return linkDefinitions(yamlFile).firstOrNull { it.keyText == linkId }
    }

    fun linkDefinitionFromKeyElement(element: PsiElement): YAMLKeyValue? {
        if (!isLinksFileName(element.containingFile?.virtualFile?.name)) return null

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
        if (!isLinkDefinition(keyValue)) return null

        return keyValue
    }

    fun linkTitle(keyValue: YAMLKeyValue): String {
        val menuLinkMapping = keyValue.value as? YAMLMapping ?: return ""
        return (menuLinkMapping.keyValues.firstOrNull { it.keyText == "title" }?.value as? YAMLScalar)
            ?.textValue
            ?.trim()
            .orEmpty()
    }

    data class RouteHandler(
        val className: String,
        val methodName: String?,
    )
}
