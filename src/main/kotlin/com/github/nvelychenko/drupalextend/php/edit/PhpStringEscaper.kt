package com.github.nvelychenko.drupalextend.php.edit

internal fun String.escapePhpSingleQuoted(): String {
    return replace("\\", "\\\\").replace("'", "\\'")
}
