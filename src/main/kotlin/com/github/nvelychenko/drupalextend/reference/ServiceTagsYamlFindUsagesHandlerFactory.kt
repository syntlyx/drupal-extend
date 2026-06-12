package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement

/**
 * Lets Find Usages / Show Usages run on a tag declaration (`tags: - { name: '…' }`) — the same
 * usage-style flow service definition keys already have: the consumers (`!tagged_iterator`,
 * `service_collector`) appear as usages with native code-line rendering, and a single usage
 * navigates straight there.
 */
class ServiceTagsYamlFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (!Services.isSupportEnabled(element.project)) return false

        return ServicesYaml.tagNameDeclarationScalar(element) != null
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        val declaration = ServicesYaml.tagNameDeclarationScalar(element) ?: return null
        return object : FindUsagesHandler(declaration) {}
    }
}
