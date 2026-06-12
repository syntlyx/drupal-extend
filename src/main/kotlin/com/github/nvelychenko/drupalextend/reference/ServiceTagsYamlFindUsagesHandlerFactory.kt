package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement

/**
 * Lets Find Usages / Show Usages run on a tag-name position (`tags: - { name: '…' }` or a
 * consumer) — the same usage-style flow service definition keys already have: the other tag
 * sites appear as usages with native code-line rendering, and a single usage navigates straight
 * there. Accepts both the scalar itself and the [ServiceTagPomTarget] wrapper the Show Usages
 * branch of Ctrl+Click hands over.
 */
class ServiceTagsYamlFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        if (!Services.isSupportEnabled(element.project)) return false

        return ServicesYaml.tagPositionScalar(ServiceTagPomTarget.unwrap(element)) != null
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        val tagScalar = ServicesYaml.tagPositionScalar(ServiceTagPomTarget.unwrap(element)) ?: return null
        return object : FindUsagesHandler(tagScalar) {}
    }
}
