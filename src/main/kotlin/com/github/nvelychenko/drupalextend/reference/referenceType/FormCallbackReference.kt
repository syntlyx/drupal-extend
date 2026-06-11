package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.reference.referenceProvider.contentRange
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Resolves a Form API callback string literal (a value under `#submit`, `#validate`, `#ajax`,
 * etc.) to the PHP element it names, enabling Ctrl-click navigation and safe rename.
 *
 * Three callback flavours are supported, each pre-classified by the provider so the reference
 * itself does no PSI walking:
 *  - [Kind.OWN_METHOD]: `'::method'` or `[$this, 'method']` → an own method of [phpClass].
 *  - [Kind.CLASS_METHOD]: `[Foo::class, 'method']` → a method of the resolved [phpClass].
 *  - [Kind.GLOBAL_FUNCTION]: `'function_name'` → a global function found in the index.
 *
 * Rename is offered only for method targets; renaming a global function from here would be
 * unsafe (the literal might collide with an unrelated string), so it is left to the platform.
 */
class FormCallbackReference(
    private val literal: StringLiteralExpression,
    private val kind: Kind,
    private val targetName: String,
    private val phpClass: PhpClass?,
    rangeInElement: TextRange = literal.contentRange(),
) : PsiReferenceBase<StringLiteralExpression>(literal, rangeInElement) {

    enum class Kind { OWN_METHOD, CLASS_METHOD, GLOBAL_FUNCTION }

    override fun resolve(): PsiElement? = when (kind) {
        Kind.OWN_METHOD, Kind.CLASS_METHOD -> phpClass?.findMethodByName(targetName)
        Kind.GLOBAL_FUNCTION ->
            PhpIndex.getInstance(literal.project).getFunctionsByName(targetName).firstOrNull()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        if (kind == Kind.GLOBAL_FUNCTION) return element
        // The literal holds only the method name for [$this/Class::class, 'method'] shapes,
        // but the leading `::` must be preserved for the '::method' string form.
        val replacement = if (literal.contents.startsWith(CALLABLE_PREFIX)) {
            CALLABLE_PREFIX + newElementName
        } else {
            newElementName
        }
        return ElementManipulators.handleContentChange(element, rangeInElement, replacement)
    }

    private companion object {
        const val CALLABLE_PREFIX = "::"
    }
}
