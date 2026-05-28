package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.isSuperInterfaceOf
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

private val ROUTE_METHOD_PARAMETERS = mapOf(
    "fromRoute" to RouteMethodParameters(routeNameIndex = 0, routeParametersIndex = 1),
    "createFromRoute" to RouteMethodParameters(routeNameIndex = 1, routeParametersIndex = 2),
    "setRedirect" to RouteMethodParameters(routeNameIndex = 0, routeParametersIndex = 1),
    "redirect" to RouteMethodParameters(routeNameIndex = 0, routeParametersIndex = 1),
    "generateFromRoute" to RouteMethodParameters(routeNameIndex = 0, routeParametersIndex = 1),
    "generate" to RouteMethodParameters(routeNameIndex = 0, routeParametersIndex = 1),
    "getRouteByName" to RouteMethodParameters(routeNameIndex = 0, routeParametersIndex = null),
)

private val ROUTE_METHOD_CLASSES = mapOf(
    "fromRoute" to setOf("\\Drupal\\Core\\Url"),
    "createFromRoute" to setOf("\\Drupal\\Core\\Link"),
    "redirect" to setOf("\\Drupal\\Core\\Controller\\ControllerBase"),
    "generateFromRoute" to setOf("\\Drupal\\Core\\Routing\\UrlGeneratorInterface"),
    "generate" to setOf(
        "\\Symfony\\Component\\Routing\\Generator\\UrlGeneratorInterface",
        "\\Symfony\\Component\\Routing\\RouterInterface",
    ),
    "getRouteByName" to setOf("\\Drupal\\Core\\Routing\\RouteProviderInterface"),
)

private val ROUTE_METHOD_INTERFACES = mapOf(
    "setRedirect" to setOf(
        "\\Drupal\\Core\\Form\\FormStateInterface",
        "\\Drupal\\Core\\Form\\FormState",
    ),
    "generateFromRoute" to setOf("\\Drupal\\Core\\Routing\\UrlGeneratorInterface"),
    "generate" to setOf(
        "\\Symfony\\Component\\Routing\\Generator\\UrlGeneratorInterface",
        "\\Symfony\\Component\\Routing\\RouterInterface",
    ),
    "getRouteByName" to setOf("\\Drupal\\Core\\Routing\\RouteProviderInterface"),
)

fun StringLiteralExpression.getRouteMethodReference(): MethodReference? {
    val parameterList = parent as? ParameterList ?: return null
    val methodReference = parameterList.parent as? MethodReference ?: return null
    val parameterIndex = ROUTE_METHOD_PARAMETERS[methodReference.name]?.routeNameIndex ?: return null

    if (parameterList.parameters.getOrNull(parameterIndex)?.isEquivalentTo(this) != true) return null

    return methodReference
}

fun MethodReference.isRouteCall(project: Project): Boolean {
    val methodName = name ?: return false
    if (!ROUTE_METHOD_PARAMETERS.containsKey(methodName)) return false

    val method = resolve() as? Method ?: return false
    val methodClass = method.containingClass ?: return false

    if (ROUTE_METHOD_CLASSES[methodName]?.contains(methodClass.fqn) == true) return true

    val phpIndex = PhpIndex.getInstance(project)
    return ROUTE_METHOD_INTERFACES[methodName]
        ?.asSequence()
        ?.flatMap { phpIndex.getInterfacesByFQN(it).asSequence() + phpIndex.getClassesByFQN(it).asSequence() }
        ?.any { methodClass.fqn == it.fqn || methodClass.isSuperInterfaceOf(it) }
        ?: false
}

fun MethodReference.routeParametersArgument(): RouteParametersArgument? {
    val methodName = name ?: return null
    val routeParametersIndex = ROUTE_METHOD_PARAMETERS[methodName]?.routeParametersIndex ?: return null
    val parameterList = children.filterIsInstance<ParameterList>().firstOrNull() ?: return null
    val argument = parameterList.parameters.getOrNull(routeParametersIndex)

    return RouteParametersArgument(
        parameterList = parameterList,
        parameterIndex = routeParametersIndex,
        argument = argument,
        arrayArgument = argument as? ArrayCreationExpression
    )
}

fun ArrayCreationExpression.routeParameterKeys(): Set<String> {
    return hashElements
        .mapNotNull { it.key as? StringLiteralExpression }
        .map { it.contents }
        .toSet()
}

fun String.normalizeRouteClassFqn(): String {
    if (isBlank()) return this
    return if (startsWith("\\")) this else "\\$this"
}

data class RouteParametersArgument(
    val parameterList: ParameterList,
    val parameterIndex: Int,
    val argument: com.intellij.psi.PsiElement?,
    val arrayArgument: ArrayCreationExpression?,
)

private data class RouteMethodParameters(
    val routeNameIndex: Int,
    val routeParametersIndex: Int?,
)
