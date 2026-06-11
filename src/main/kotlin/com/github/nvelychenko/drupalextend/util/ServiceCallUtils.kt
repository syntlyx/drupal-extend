package com.github.nvelychenko.drupalextend.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.Base64

private const val DRUPAL_CLASS = "\\Drupal"
private const val SERVICE_TYPE_SIGNATURE_PREFIX = "drupalExtendService:"
private const val AUTOWIRE_ATTRIBUTE_FQN = "\\Symfony\\Component\\DependencyInjection\\Attribute\\Autowire"

fun MethodReference.isDrupalServiceCall(): Boolean {
    if (name != "service") return false
    if (!isStatic) return false

    return classReference?.text?.trim()?.removePrefix("\\") == DRUPAL_CLASS.removePrefix("\\")
}

fun MethodReference.isContainerGetCall(): Boolean {
    if (name != "get") return false
    if (isStatic) return false

    val receiverText = classReference?.text?.lowercase() ?: return false
    return "container" in receiverText
}

fun StringLiteralExpression.getServiceMethodReference(): MethodReference? {
    val parameterList = parent as? ParameterList ?: return null
    if (parameterList.parameters.firstOrNull()?.isEquivalentTo(this) != true) return null

    return parameterList.parent as? MethodReference
}

/**
 * True when this literal is the service id of an `#[Autowire]` attribute — either the
 * `service:` named argument or the first positional argument (Symfony folds both into the
 * autowiring reference, and Drupal's AutowireTrait reads the resulting value).
 */
fun StringLiteralExpression.isAutowireServiceArgument(): Boolean {
    val parameterList = parent as? ParameterList ?: return false
    val attribute = parameterList.parent as? PhpAttribute ?: return false
    if (attribute.fqn != AUTOWIRE_ATTRIBUTE_FQN) return false

    val argumentName = attributeArgumentName()

    return argumentName == "service" ||
            (argumentName == null && parameterList.parameters.firstOrNull()?.isEquivalentTo(this) == true)
}

/** The name of the named argument this literal belongs to, or null for a positional argument. */
private fun StringLiteralExpression.attributeArgumentName(): String? {
    val colon = prevNonWhitespaceSibling(this) ?: return null
    if (colon.node?.elementType != PhpTokenTypes.opCOLON) return null

    return prevNonWhitespaceSibling(colon)?.text
}

private fun prevNonWhitespaceSibling(element: PsiElement): PsiElement? {
    var sibling = element.prevSibling
    while (sibling is PsiWhiteSpace) {
        sibling = sibling.prevSibling
    }

    return sibling
}

fun String.normalizeServiceClassFqn(): String {
    if (isBlank()) return this
    return if (startsWith("\\")) this else "\\$this"
}

fun String.encodeServiceIdForTypeSignature(): String {
    return SERVICE_TYPE_SIGNATURE_PREFIX + Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(toByteArray(Charsets.UTF_8))
}

fun String.decodeServiceIdFromTypeSignature(): String? {
    if (!startsWith(SERVICE_TYPE_SIGNATURE_PREFIX)) return null
    return runCatching {
        String(
            Base64.getUrlDecoder().decode(removePrefix(SERVICE_TYPE_SIGNATURE_PREFIX)),
            Charsets.UTF_8
        )
    }.getOrNull()
}
