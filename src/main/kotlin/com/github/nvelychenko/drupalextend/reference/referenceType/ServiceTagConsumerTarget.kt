package com.github.nvelychenko.drupalextend.reference.referenceType

import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * Presentation wrapper for a tag-consumer navigation target. The raw target is the
 * `!tagged_iterator` scalar, whose default popup row is just the tag text — identical for every
 * consumer. Wrapping gives the chooser the native goto look: file icon, the trimmed code line,
 * and the containing file name; navigation still lands exactly on the wrapped element.
 */
internal class ServiceTagConsumerTarget(private val target: PsiElement) : FakePsiElement() {

    override fun getParent(): PsiElement? = target.parent

    override fun getNavigationElement(): PsiElement = target

    override fun canNavigate(): Boolean = (target.navigationElement as? Navigatable)?.canNavigate() == true

    override fun navigate(requestFocus: Boolean) {
        (target.navigationElement as? Navigatable)?.navigate(requestFocus)
    }

    override fun getName(): String = targetLineText()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = targetLineText()

        override fun getLocationString(): String? = target.containingFile?.name

        override fun getIcon(unused: Boolean): Icon? = target.containingFile?.getIcon(0)
    }

    /** The trimmed source line holding the target — the "code" part of the popup row. */
    private fun targetLineText(): String {
        val file = target.containingFile ?: return target.text.orEmpty()
        val text = file.text ?: return target.text.orEmpty()
        val offset = target.textRange.startOffset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).plus(1)
        val lineEnd = text.indexOf('\n', offset).takeIf { it >= 0 } ?: text.length

        return text.substring(lineStart, lineEnd).trim()
    }
}
