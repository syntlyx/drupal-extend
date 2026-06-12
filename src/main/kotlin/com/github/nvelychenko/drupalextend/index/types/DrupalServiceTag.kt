package com.github.nvelychenko.drupalextend.index.types

import kotlinx.serialization.Serializable

/**
 * One `*.services.yml` file's relationship to a given tag: the services carrying it under
 * `tags:` and the services consuming it via `!tagged_iterator` / `!tagged_locator` arguments.
 */
@Serializable
data class DrupalServiceTag(
    val tagName: String,
    val serviceIds: List<String>,
    val consumerServiceIds: List<String> = emptyList(),
)
