package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.reference.referenceProvider.contentRange
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Resolves a method-name string literal inside `getSubscribedEvents()` to the own method it
 * registers, enabling Ctrl-click navigation and safe rename.
 */
class SubscribedEventMethodReference(
    private val literal: StringLiteralExpression,
    private val phpClass: PhpClass,
    rangeInElement: TextRange = literal.contentRange(),
) : PsiReferenceBase<StringLiteralExpression>(literal, rangeInElement) {

    private val methodName = literal.contents

    override fun resolve(): PsiElement? = phpClass.findOwnMethodByName(methodName)

    override fun handleElementRename(newElementName: String): PsiElement =
        ElementManipulators.handleContentChange(element, rangeInElement, newElementName)
}
