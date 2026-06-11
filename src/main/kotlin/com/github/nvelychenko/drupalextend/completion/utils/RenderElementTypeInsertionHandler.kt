package com.github.nvelychenko.drupalextend.completion.utils

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

class RenderElementTypeInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document

        // startOffset can be 0 when the completed element starts the file.
        if (context.startOffset <= 0) {
            return
        }

        if (document.charsSequence[context.startOffset - 1] != '#') {
            context.document.insertString(context.startOffset, "#")
        }
    }
}
