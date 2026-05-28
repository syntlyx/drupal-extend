package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

private const val DRUPAL_CLASS = "\\Drupal"
private const val CURRENT_ROUTE_MATCH_SERVICE_ID = "current_route_match"
private const val CURRENT_ROUTE_MATCH_TYPE = "\\Drupal\\Core\\Routing\\RouteMatchInterface"
private const val CURRENT_USER_SERVICE_ID = "current_user"
private const val CURRENT_USER_TYPE = "\\Drupal\\Core\\Session\\AccountProxyInterface"
private val PREFERRED_SERVICE_PROPERTY_NAMES = mapOf(
    CURRENT_ROUTE_MATCH_SERVICE_ID to "routeMatch",
    CURRENT_USER_SERVICE_ID to "currentUser",
)

internal data class DrupalStaticServiceCall(
    val methodReference: MethodReference,
    val serviceId: String,
    val serviceClass: String,
    val propertyName: String,
    val parameterName: String,
) {
    fun targetClassForInjection(): PhpClass? {
        val method = PsiTreeUtil.getParentOfType(methodReference, Method::class.java, false) ?: return null
        if (method.isStatic || method.name == PhpClass.CONSTRUCTOR || method.name == "create") return null
        val phpClass = method.containingClass ?: return null
        if (phpClass.isInterface || phpClass.isTrait || phpClass.isEnum || phpClass.isAnonymous) return null
        if (phpClass.fqn == DRUPAL_CLASS) return null

        return phpClass
    }

    companion object {
        fun from(project: Project, methodReference: MethodReference): DrupalStaticServiceCall? {
            if (!methodReference.isDrupalClassStaticCall()) return null

            val serviceId = when (methodReference.name) {
                "service" -> methodReference.firstStringArgument()?.contents?.trim()?.takeIf { it.isNotBlank() }
                "routeMatch" -> CURRENT_ROUTE_MATCH_SERVICE_ID
                "currentUser" -> CURRENT_USER_SERVICE_ID
                else -> null
            } ?: return null

            val serviceClass = when (methodReference.name) {
                "routeMatch" -> serviceClassOrFallback(project, serviceId, CURRENT_ROUTE_MATCH_TYPE)
                "currentUser" -> serviceClassOrFallback(project, serviceId, CURRENT_USER_TYPE)
                else -> Services.className(project, serviceId)?.normalizeServiceClassFqn()
            }?.takeIf { it.isNotBlank() } ?: return null
            val propertyName = propertyNameFor(serviceId, serviceClass, methodReference.name)

            return DrupalStaticServiceCall(
                methodReference = methodReference,
                serviceId = serviceId,
                serviceClass = serviceClass,
                propertyName = propertyName,
                parameterName = propertyName.toSnakeCase()
            )
        }

        private fun serviceClassOrFallback(project: Project, serviceId: String, fallback: String): String {
            return Services.className(project, serviceId)
                ?.normalizeServiceClassFqn()
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        }
    }
}

private fun MethodReference.isDrupalClassStaticCall(): Boolean {
    if (!isStatic) return false

    val resolvedMethod = resolve() as? Method
    if (resolvedMethod?.containingClass?.fqn == DRUPAL_CLASS) return true

    return classReference?.text?.trim() == DRUPAL_CLASS
}

private fun MethodReference.firstStringArgument(): StringLiteralExpression? {
    return parameterList?.getParameter(0) as? StringLiteralExpression
}

private fun propertyNameFor(serviceId: String, serviceClass: String, methodName: String?): String {
    PREFERRED_SERVICE_PROPERTY_NAMES[serviceId]?.let { return it }

    val classShortName = serviceClass
        .substringAfterLast("\\")
        .removeSuffix("Interface")
        .takeIf { it.isNotBlank() && it != "object" }
    if (classShortName != null) {
        return classShortName.replaceFirstChar { it.lowercaseChar() }
    }

    val parts = serviceId.split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
    val propertyName = parts.mapIndexed { index, part ->
        part.replaceFirstChar {
            if (index == 0) it.lowercaseChar() else it.uppercaseChar()
        }
    }.joinToString("")
        .takeIf { it.isNotBlank() }
        ?: methodName.orEmpty().takeIf { it.isNotBlank() }
        ?: "service"

    return if (propertyName.first().isDigit()) {
        "service${propertyName.replaceFirstChar { it.uppercaseChar() }}"
    } else {
        propertyName
    }
}

private fun String.toSnakeCase(): String {
    return replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
}
