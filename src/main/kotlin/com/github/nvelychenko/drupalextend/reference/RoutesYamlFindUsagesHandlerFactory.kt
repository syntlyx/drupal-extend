package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement

class RoutesYamlFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (!Routes.isSupportEnabled(element.project)) return false

        return definitionFromKeyElement(element) != null
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        val definition = definitionFromKeyElement(element) ?: return null
        return object : FindUsagesHandler(definition.key ?: definition) {}
    }

    private fun definitionFromKeyElement(element: PsiElement): org.jetbrains.yaml.psi.YAMLKeyValue? {
        return RoutingYaml.routeDefinitionFromKeyElement(element)
            ?: RoutingYaml.linkDefinitionFromKeyElement(element)
            ?: Permissions.permissionDefinitionFromKeyElement(element)
    }
}
