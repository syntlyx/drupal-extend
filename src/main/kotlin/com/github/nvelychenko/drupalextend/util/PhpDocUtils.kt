package com.github.nvelychenko.drupalextend.util

/**
 * Extracts the value of an annotation key like `id = "node"` from a docblock text.
 *
 * The key must not be preceded by a word character or a quote, so `id` does not match
 * `field_id = "..."`, `valid = "..."`, or the quoted entity-keys form `"id" = "nid"`
 * (callers that want the quoted form pass the id with quotes included).
 */
fun getPhpDocParameter(phpDocText: String, id: String): String? {
    val entityTypeMatch = Regex("(?<![\\w\"])${Regex.escape(id)}\\s*=\\s*\"([^\"]+)\"").find(phpDocText)

    return entityTypeMatch?.groups?.get(1)?.value
}
