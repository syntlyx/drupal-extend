package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.index.CONFIG_OBJECT_INDEX_KEY
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

/** Navigates a config object name to its defining YAML file(s) (install/optional/sync). */
class ConfigObjectReference(
    element: PsiElement,
    private val configName: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project

    override fun getCanonicalText(): String {
        return configName
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!project.drupalExtendSettings.isEnabled) return ResolveResult.EMPTY_ARRAY

        val files = mutableListOf<VirtualFile>()
        FileBasedIndex.getInstance().processValues(
            CONFIG_OBJECT_INDEX_KEY,
            configName,
            null,
            { file, _ -> files.add(file); true },
            GlobalSearchScope.allScope(project),
        )

        val psiManager = PsiManager.getInstance(project)
        val targets = files.mapNotNull { psiManager.findFile(it) }

        return PsiElementResolveResult.createResults(targets)
    }
}
