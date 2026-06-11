package com.github.nvelychenko.drupalextend.index.types

import kotlinx.serialization.Serializable

/** The services of one `*.services.yml` file carrying a given tag. */
@Serializable
data class DrupalServiceTag(
    val tagName: String,
    val serviceIds: List<String>,
)
