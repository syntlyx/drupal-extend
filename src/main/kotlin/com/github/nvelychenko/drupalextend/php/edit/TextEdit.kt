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
    // Edits are applied in descending offset order so earlier offsets stay valid. That is only
    // correct for NON-overlapping edits: an edit reaching into an already-applied region would
    // cut at stale offsets and corrupt the document. The aggregating fixers collect edits from
    // many independent sources, so overlaps are dropped here defensively — the skipped fix
    // simply reappears on the next pass over the already-corrected text.
    var appliedRegionStart = Int.MAX_VALUE
    sortedWith(compareByDescending<TextEdit> { it.startOffset }.thenByDescending { it.endOffset })
        .forEach { edit ->
            if (edit.endOffset > appliedRegionStart) return@forEach
            document.replaceString(edit.startOffset, edit.endOffset, edit.text)
            appliedRegionStart = edit.startOffset
        }
}
