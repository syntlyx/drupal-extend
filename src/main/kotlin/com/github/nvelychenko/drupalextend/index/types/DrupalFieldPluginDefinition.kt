package com.github.nvelychenko.drupalextend.index.types

import kotlinx.serialization.Serializable

@Serializable
data class DrupalFieldPluginDefinition(
    val kind: String,
    val id: String,
    val defaultWidget: String? = null,
    val defaultFormatter: String? = null,
)
