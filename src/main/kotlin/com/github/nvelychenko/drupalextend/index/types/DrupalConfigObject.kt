package com.github.nvelychenko.drupalextend.index.types

import kotlinx.serialization.Serializable

/**
 * A configuration object discovered from a `config/install`, `config/optional`, or sync-export
 * YAML file. [keyPaths] are the flattened dotted key paths of the file's content
 * (e.g. `page.front`), the shape `Config::get()` consumes.
 */
@Serializable
data class DrupalConfigObject(
    val name: String,
    val keyPaths: List<String>,
)
