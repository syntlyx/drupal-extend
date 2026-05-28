package com.github.nvelychenko.drupalextend.documentation

import com.github.nvelychenko.drupalextend.index.types.DrupalRoute
import com.github.nvelychenko.drupalextend.util.Links
import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLScalar

class RoutesYamlDocumentationProvider : AbstractDocumentationProvider() {
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
        if (!Routes.isSupportEnabled(project)) return null
        val fileName = contextElement.containingFile?.virtualFile?.name
        if (!RoutingYaml.isRouteReferenceFileName(fileName) && fileName?.endsWith(".permissions.yml") != true) {
            return null
        }

        RoutingYaml.routeDefinitionFromKeyElement(contextElement)?.let { routeDefinition ->
            return DocumentationTarget.Route(
                routeDefinition.keyText,
                route = Routes.definition(project, routeDefinition.keyText)
                    ?: routeDefinition.toLocalRoute()
            )
        }
        RoutingYaml.linkDefinitionFromKeyElement(contextElement)?.let { linkDefinition ->
            return DocumentationTarget.Link(
                linkDefinition.keyText,
                title = RoutingYaml.linkTitle(linkDefinition).takeIf { it.isNotBlank() }
            )
        }
        Permissions.permissionDefinitionFromKeyElement(contextElement)?.let { permissionDefinition ->
            return DocumentationTarget.Permission(
                permissionDefinition.keyText,
                title = Permissions.title(project, permissionDefinition.keyText)
            )
        }

        val scalar = contextElement as? YAMLScalar
            ?: PsiTreeUtil.getParentOfType(contextElement, YAMLScalar::class.java, false)
            ?: return null
        if (RoutingYaml.isKey(scalar)) return null

        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return null
        if (RoutingYaml.isRouteReferenceKeyPath(keyPath)) {
            val routeName = scalar.textValue.trim().takeIf { it.isNotBlank() } ?: return null
            return DocumentationTarget.Route(routeName, Routes.definition(project, routeName))
        }

        if (RoutingYaml.isLinkParentKeyPath(keyPath)) {
            val linkId = scalar.textValue.trim().takeIf { it.isNotBlank() } ?: return null
            return DocumentationTarget.Link(linkId, Links.title(project, linkId))
        }

        if (Permissions.isYamlPermissionKeyPath(keyPath)) {
            val permissionName = Permissions.permissionTokenAt(scalar, contextElement)?.name ?: return null
            return DocumentationTarget.Permission(permissionName, Permissions.title(project, permissionName))
        }

        return null
    }

    private fun PsiElement.toLocalRoute(): DrupalRoute? {
        val routeDefinition = RoutingYaml.routeDefinitionFromKeyElement(this) ?: return null
        val handler = RoutingYaml.routeHandler(routeDefinition)
        return DrupalRoute(
            name = routeDefinition.keyText,
            path = RoutingYaml.routePath(routeDefinition),
            handlerClass = handler?.className,
            handlerMethod = handler?.methodName
        )
    }

    private sealed class DocumentationTarget {
        abstract fun quickNavigateInfo(): String
        abstract fun documentation(): String

        data class Route(
            val routeName: String,
            val route: DrupalRoute?,
        ) : DocumentationTarget() {
            override fun quickNavigateInfo(): String {
                return buildString {
                    append(routeName)
                    route?.path?.takeIf { it.isNotBlank() }?.let { append(" -> ").append(it) }
                }
            }

            override fun documentation(): String {
                return buildDocumentation(
                    definition = "route ${routeName.escape()}",
                    sections = buildList {
                        route?.description?.takeIf { it.isNotBlank() }?.let { add("Description" to it) }
                        route?.path?.takeIf { it.isNotBlank() }?.let { add("Path" to it) }
                        route?.handler()?.let { add("Handler" to it) }
                    }
                )
            }

            private fun DrupalRoute.handler(): String? {
                val handlerClass = handlerClass?.takeIf { it.isNotBlank() } ?: return null
                return handlerMethod
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$handlerClass::$it" }
                    ?: handlerClass
            }
        }

        data class Link(
            val linkId: String,
            val title: String?,
        ) : DocumentationTarget() {
            override fun quickNavigateInfo(): String {
                return buildString {
                    append(linkId)
                    title?.takeIf { it.isNotBlank() }?.let { append(" -> ").append(it) }
                }
            }

            override fun documentation(): String {
                return buildDocumentation(
                    definition = "link ${linkId.escape()}",
                    sections = buildList {
                        title?.takeIf { it.isNotBlank() }?.let { add("Title" to it) }
                    }
                )
            }
        }

        data class Permission(
            val permissionName: String,
            val title: String?,
        ) : DocumentationTarget() {
            override fun quickNavigateInfo(): String {
                return buildString {
                    append(permissionName)
                    title?.takeIf { it.isNotBlank() }?.let { append(" -> ").append(it) }
                }
            }

            override fun documentation(): String {
                return buildDocumentation(
                    definition = "permission ${permissionName.escape()}",
                    sections = buildList {
                        title?.takeIf { it.isNotBlank() }?.let { add("Title" to it) }
                    }
                )
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
