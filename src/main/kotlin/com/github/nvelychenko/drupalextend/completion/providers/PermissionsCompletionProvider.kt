package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.*
import com.jetbrains.php.lang.psi.elements.impl.MethodImpl

class PermissionsCompletionProvider : CompletionProvider<CompletionParameters>() {

    companion object {
        val allowedMethods: Map<String, Map<String, Int>> = mapOf(
            "hasPermission" to mapOf(
                "\\Drupal\\Core\\Session\\AccountInterface.hasPermission" to 0,
                "\\Drupal\\user\\RoleInterface.hasPermission" to 0,
                "\\Drupal\\user\\Entity\\Role.hasPermission" to 0,
            ),
            "grantPermission" to mapOf(
                "\\Drupal\\user\\RoleInterface.grantPermission" to 0,
                "\\Drupal\\user\\Entity\\Role.grantPermission" to 0
            ),
            "revokePermission" to mapOf(
                "\\Drupal\\user\\RoleInterface.revokePermission" to 0,
                "\\Drupal\\user\\Entity\\Role.revokePermission" to 0,
            ),
            "allowedIfHasPermission" to mapOf(
                "\\Drupal\\Core\\Access\\AccessResult.allowedIfHasPermission" to 1,
            )
        )
    }

    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project
        if (!project.drupalExtendSettings.isEnabled) return
        val element = leaf.parent as? StringLiteralExpression ?: return

        val parameterList = element.parent as? ParameterList ?: return
        val methodReference = parameterList.parent as? MethodReference ?: return
        val methodName = methodReference.name
        val position = allowedMethods.keys.find { it == methodName } ?: return
        val methodsToCheck = allowedMethods[position] ?: return
        val currentElementMethodFqn = (methodReference.resolve() as? MethodImpl)?.fqn ?: return
        val methodToCheck = methodsToCheck.keys.find { it == currentElementMethodFqn } ?: return
        val permissionArgumentIndex = methodsToCheck[methodToCheck] ?: return
        // The call may not have that many arguments yet (e.g. a single-argument
        // allowedIfHasPermission() mid-typing) — never index past the actual argument list.
        val permissionArgument = parameterList.parameters.getOrNull(permissionArgumentIndex) ?: return
        if (!permissionArgument.isEquivalentTo(element)) return

        FileBasedIndex.getInstance()
            .getAllProjectKeys(PERMISSIONS_INDEX_KEY, project)
            .forEach {
                completionResultSet.addElement(LookupElementBuilder.create(it))
            }
    }

}
