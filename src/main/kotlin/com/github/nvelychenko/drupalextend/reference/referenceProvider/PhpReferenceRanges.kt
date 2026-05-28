package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.intellij.openapi.util.TextRange
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

internal fun StringLiteralExpression.contentRange(): TextRange {
    val literalText = text
    val quote = literalText.firstOrNull()
    if (quote == '\'' || quote == '"') {
        val endOffset = if (literalText.lastOrNull() == quote) {
            literalText.length - 1
        } else {
            literalText.length
        }

        return TextRange(1, endOffset.coerceAtLeast(1))
    }

    val contentStart = literalText.indexOf(contents)
    if (contentStart >= 0) {
        return TextRange(contentStart, contentStart + contents.length)
    }

    return TextRange(0, textLength)
}
