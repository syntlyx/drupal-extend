package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.types.DrupalRoute
import com.github.nvelychenko.drupalextend.index.types.DrupalRouteHandler
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

object Routes {
    private val entityRouteDefinitions = mapOf(
        "canonical" to EntityRouteDefinition(requiresEntityParameter = true, description = "Entity canonical route."),
        "edit_form" to EntityRouteDefinition(requiresEntityParameter = true, description = "Entity edit form route."),
        "delete_form" to EntityRouteDefinition(requiresEntityParameter = true, description = "Entity delete form route."),
        "add_form" to EntityRouteDefinition(requiresEntityParameter = false, description = "Entity add form route."),
        "add_page" to EntityRouteDefinition(requiresEntityParameter = false, description = "Entity add page route."),
        "collection" to EntityRouteDefinition(requiresEntityParameter = false, description = "Entity collection route."),
    )

    private val builtInRoutes = listOf(
        DrupalRoute(
            name = "<current>",
            path = "current route",
            description = "The currently matched route."
        ),
        DrupalRoute(
            name = "<front>",
            path = "/",
            description = "The site's front page route."
        ),
        DrupalRoute(
            name = "<none>",
            path = "",
            description = "A route placeholder that renders no link destination."
        ),
    ).associateBy { it.name }

    fun isSupportEnabled(project: Project): Boolean {
        return project.drupalExtendSettings.isEnabled
                && project.drupalExtendSettings.isRoutesCompletionEnabled
    }

    fun allNames(project: Project): Collection<String> {
        if (!isSupportEnabled(project)) return emptyList()
        return (builtInRoutes.keys + FileBasedIndex.getInstance()
            .getAllProjectKeys(ROUTES_INDEX_KEY, project)
            .toSet() + allDynamicEntityRouteNames(project))
            .toSet()
            .sorted()
    }

    fun definition(project: Project, routeName: String): DrupalRoute? {
        if (!isSupportEnabled(project)) return null
        builtInRoutes[routeName]?.let { return it }
        dynamicViewRoute(routeName)?.let { return it }
        dynamicEntityRoute(project, routeName)?.let { return it }
        return FileBasedIndex.getInstance().getValue(ROUTES_INDEX_KEY, routeName, project)
    }

    fun exists(project: Project, routeName: String): Boolean {
        return definition(project, routeName) != null
    }

    fun referencesHandler(project: Project, classFqn: String, methodName: String? = null): Boolean {
        if (!isSupportEnabled(project)) return false

        val expectedClass = classFqn.normalizeRouteClassFqn()
        val index = FileBasedIndex.getInstance()

        return index.getAllProjectKeys(ROUTES_INDEX_KEY, project)
            .flatMap { index.getValues(ROUTES_INDEX_KEY, it, GlobalSearchScope.allScope(project)) }
            .flatMap { route -> route.allHandlers() }
            .any { handler ->
                handler.className.normalizeRouteClassFqn() == expectedClass &&
                        (methodName == null || handler.methodName == methodName)
            }
    }

    private fun allDynamicEntityRouteNames(project: Project): Collection<String> {
        val entityTypeIds = allEntityTypeIds(project)
        val entityRouteNames = entityTypeIds.flatMap { entityTypeId ->
            entityRouteDefinitions.keys.map { routeSuffix -> "entity.$entityTypeId.$routeSuffix" }
        }

        return entityRouteNames + if ("node" in entityTypeIds) {
            listOf("node.add", "node.add_page", "entity.node.add_form", "entity.node.add_page")
        } else {
            emptyList()
        }
    }

    private fun dynamicEntityRoute(project: Project, routeName: String): DrupalRoute? {
        dynamicNodeRoute(project, routeName)?.let { return it }
        if (!routeName.startsWith("entity.")) return null

        val routeNameParts = routeName.removePrefix("entity.").split(".")
        if (routeNameParts.size < 2) return null

        val routeSuffix = routeNameParts.last()
        val entityRouteDefinition = entityRouteDefinitions[routeSuffix] ?: return null
        val entityTypeId = routeNameParts.dropLast(1).joinToString(".")
            .takeIf { it.isNotBlank() } ?: return null
        if (entityTypeId !in allEntityTypeIds(project)) return null

        return DrupalRoute(
            name = routeName,
            path = "",
            parameters = if (entityRouteDefinition.requiresEntityParameter) listOf(entityTypeId) else emptyList(),
            description = entityRouteDefinition.description
        )
    }

    private fun dynamicNodeRoute(project: Project, routeName: String): DrupalRoute? {
        if ("node" !in allEntityTypeIds(project)) return null

        return when (routeName) {
            "node.add", "entity.node.add_form" -> DrupalRoute(
                name = routeName,
                path = "/node/add/{node_type}",
                parameters = listOf("node_type"),
                description = if (routeName == "node.add") {
                    "Node add form route."
                } else {
                    "Alias of the node.add route."
                }
            )

            "node.add_page", "entity.node.add_page" -> DrupalRoute(
                name = routeName,
                path = "/node/add",
                description = if (routeName == "node.add_page") {
                    "Node add page route."
                } else {
                    "Alias of the node.add_page route."
                }
            )

            else -> null
        }
    }

    private fun dynamicViewRoute(routeName: String): DrupalRoute? {
        val match = VIEW_ROUTE.matchEntire(routeName) ?: return null
        val viewId = match.groupValues[1]
        val displayId = match.groupValues[2]

        return DrupalRoute(
            name = routeName,
            path = "",
            description = "Views display route for view '$viewId' and display '$displayId'."
        )
    }

    private fun allEntityTypeIds(project: Project): Set<String> {
        val index = FileBasedIndex.getInstance()
        return (index.getAllProjectKeys(CONTENT_ENTITY_INDEX_KEY, project)
                + index.getAllProjectKeys(CONFIG_ENTITY_INDEX_KEY, project))
            .toSet()
    }

    private data class EntityRouteDefinition(
        val requiresEntityParameter: Boolean,
        val description: String,
    )
}

private val VIEW_ROUTE = Regex("""view\.([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)""")

private fun DrupalRoute.allHandlers(): List<DrupalRouteHandler> {
    if (handlers.isNotEmpty()) return handlers

    return handlerClass
        ?.let { listOf(DrupalRouteHandler(it, handlerMethod)) }
        ?: emptyList()
}
