package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement

class ServicesYamlFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (!Services.isSupportEnabled(element.project)) return false

        return ServicesYaml.serviceDefinitionFromKeyElement(element) != null
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        val serviceDefinition = ServicesYaml.serviceDefinitionFromKeyElement(element) ?: return null
        return object : FindUsagesHandler(serviceDefinition.key ?: serviceDefinition) {}
    }
}
