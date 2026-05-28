package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Links
import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLScalar

class RoutesYamlCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val position = completionParameters.originalPosition ?: return
        val project = position.project

        if (!Routes.isSupportEnabled(project)) return

        val scalar = PsiTreeUtil.getParentOfType(position, YAMLScalar::class.java, false) ?: return
        if (!RoutingYaml.isRouteReferenceFileName(scalar.containingFile.virtualFile?.name)) return
        if (RoutingYaml.isKey(scalar)) return

        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return
        if (addRouteCompletions(scalar, keyPath, completionResultSet)) return
        if (addLinkParentCompletions(scalar, keyPath, completionResultSet)) return
        if (addPermissionCompletions(scalar, keyPath, completionParameters, completionResultSet)) return
    }

    private fun addRouteCompletions(
        scalar: YAMLScalar,
        keyPath: String,
        completionResultSet: CompletionResultSet
    ): Boolean {
        if (!RoutingYaml.isRouteReferenceKeyPath(keyPath)) return false

        val project = scalar.project
        val routeResultSet = completionResultSet.withPrefixMatcher(scalar.textValue.trim())
        Routes.allNames(project)
            .forEach { routeName ->
                val route = Routes.definition(project, routeName)
                var lookupElement = LookupElementBuilder.create(routeName)

                if (!route?.path.isNullOrBlank()) {
                    lookupElement = lookupElement.withTypeText(route.path, true)
                }

                routeResultSet.addElement(lookupElement)
            }

        completionResultSet.stopHere()
        return true
    }

    private fun addLinkParentCompletions(
        scalar: YAMLScalar,
        keyPath: String,
        completionResultSet: CompletionResultSet
    ): Boolean {
        if (!RoutingYaml.isLinksFileName(scalar.containingFile.virtualFile?.name)) return false
        if (!RoutingYaml.isLinkParentKeyPath(keyPath)) return false

        val linkResultSet = completionResultSet.withPrefixMatcher(scalar.textValue.trim())
        Links.allIds(scalar.project)
            .forEach { linkId ->
                val title = Links.title(scalar.project, linkId)
                var lookupElement = LookupElementBuilder.create(linkId)

                if (!title.isNullOrBlank()) {
                    lookupElement = lookupElement.withTypeText(title, true)
                }

                linkResultSet.addElement(lookupElement)
            }

        completionResultSet.stopHere()
        return true
    }

    private fun addPermissionCompletions(
        scalar: YAMLScalar,
        keyPath: String,
        completionParameters: CompletionParameters,
        completionResultSet: CompletionResultSet
    ): Boolean {
        if (!Permissions.isYamlPermissionReferenceFileName(scalar.containingFile.virtualFile?.name)) return false
        if (!Permissions.isYamlPermissionKeyPath(keyPath)) return false

        val permissionResultSet = completionResultSet.withPrefixMatcher(
            Permissions.currentPermissionPrefix(scalar, completionParameters.offset)
        )
        Permissions.allNames(scalar.project)
            .forEach { permissionName ->
                permissionResultSet.addElement(LookupElementBuilder.create(permissionName))
            }

        completionResultSet.stopHere()
        return true
    }
}
