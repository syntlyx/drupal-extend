package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.SubscribedEventMethodReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Turns a method-name string inside `getSubscribedEvents()` into a reference to the own method
 * it registers. Narrows on the broad string pattern it is registered against: the literal must
 * sit inside an own method literally named `getSubscribedEvents`.
 */
class SubscribedEventMethodReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (!element.project.drupalExtendSettings.isEnabled) return emptyArray()

        val literal = element as? StringLiteralExpression ?: return emptyArray()
        if (literal.contents.isEmpty()) return emptyArray()

        val method = PsiTreeUtil.getParentOfType(literal, Method::class.java) ?: return emptyArray()
        if (method.name != SUBSCRIBED_EVENTS_METHOD) return emptyArray()

        val phpClass = method.containingClass ?: return emptyArray()
        // Only the class's own getSubscribedEvents registers its own handler methods.
        if (phpClass.findOwnMethodByName(SUBSCRIBED_EVENTS_METHOD)?.isEquivalentTo(method) != true) {
            return emptyArray()
        }

        if (phpClass.findOwnMethodByName(literal.contents) == null) return emptyArray()

        return arrayOf(SubscribedEventMethodReference(literal, phpClass))
    }

    private companion object {
        const val SUBSCRIBED_EVENTS_METHOD = "getSubscribedEvents"
    }
}
