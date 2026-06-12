package com.github.nvelychenko.drupalextend.reference.referenceType

import com.intellij.psi.PsiElement
import com.intellij.util.xml.model.gotosymbol.GoToSymbolProvider.BaseNavigationItem

/**
 * Presentation wrapper for a tag-consumer navigation target. The raw target is the
 * `!tagged_iterator` scalar, whose default popup row is just the tag text — identical for every
 * consumer. [BaseNavigationItem] gives the chooser the native goto look (file icon, the trimmed
 * code line, the containing file name) and — unlike a hand-rolled [ItemPresentation]-only
 * wrapper — survives the popup's asynchronous re-render, which reads the icon back through
 * `getIcon(flags)`. Same pattern as [ThemeElementReference]. Navigation still lands exactly on
 * the wrapped element via `getNavigationElement()`.
 */
internal class ServiceTagConsumerTarget(target: PsiElement) : BaseNavigationItem(
    target,
    targetLineText(target),
    target.containingFile?.getIcon(0),
)

/** The trimmed source line holding the target — the "code" part of the popup row. */
private fun targetLineText(target: PsiElement): String {
    val text = target.containingFile?.text ?: return target.text.orEmpty()
    val offset = target.textRange.startOffset.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).plus(1)
    val lineEnd = text.indexOf('\n', offset).takeIf { it >= 0 } ?: text.length

    return text.substring(lineStart, lineEnd).trim()
}
