package com.github.nvelychenko.drupalextend.index.types

import kotlinx.serialization.Serializable

@Serializable
data class DrupalRoute(
    val name: String,
    val path: String,
    val parameters: List<String> = emptyList(),
    val handlerClass: String? = null,
    val handlerMethod: String? = null,
    val handlers: List<DrupalRouteHandler> = emptyList(),
    val description: String? = null,
)

@Serializable
data class DrupalRouteHandler(
    val className: String,
    val methodName: String? = null,
)
