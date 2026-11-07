package com.github.nvelychenko.drupalextend.util

import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.jetbrains.php.lang.psi.elements.PhpAttribute

/**
 * Extracts a string argument of an attribute by name, falling back to the positional
 * argument at [position]. Returns null for non-literal values (constants, expressions) —
 * only quoted string literals are trusted, so the result is safe to use as an index key.
 */
internal fun PhpAttribute.stringArgument(name: String, position: Int? = null): String? {
    val value = arguments.firstOrNull { it.name == name }?.argument?.value
        ?: position?.let { index ->
            arguments.filter { it.name.isNullOrEmpty() }.getOrNull(index)?.argument?.value
        }
        ?: return null

    val unquotedValue = unquoteString(value)
    if (unquotedValue == value && !value.startsWith("'") && !value.startsWith("\"")) {
        return null
    }

    return unquotedValue.takeIf { it.isNotBlank() }
}
