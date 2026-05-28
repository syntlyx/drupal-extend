package com.github.nvelychenko.drupalextend.php.edit

import com.intellij.openapi.editor.Document

internal data class TextEdit(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
) {
    constructor(offset: Int, text: String) : this(offset, offset, text)
}

internal fun Collection<TextEdit>.applyTo(document: Document) {
    sortedWith(compareByDescending<TextEdit> { it.startOffset }.thenByDescending { it.endOffset })
        .forEach { edit ->
            document.replaceString(edit.startOffset, edit.endOffset, edit.text)
        }
}
