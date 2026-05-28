package com.github.nvelychenko.drupalextend.util

import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.Base64

private const val DRUPAL_CLASS = "\\Drupal"
private const val SERVICE_TYPE_SIGNATURE_PREFIX = "drupalExtendService:"

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
