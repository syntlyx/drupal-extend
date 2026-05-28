package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.github.nvelychenko.drupalextend.util.yml.YAMLKeyValueFinder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

class ServiceReference(
    element: PsiElement,
    private val serviceId: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project
    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

    override fun getCanonicalText(): String {
        return serviceId
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (ServicesYaml.serviceDefinitionFromKeyElement(element)?.keyText == serviceId) return true
        return super.isReferenceTo(element)
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Services.isSupportEnabled(project)) return emptyArray()

        val resolveResults = mutableListOf<ResolveResult>()
        val processor = FileBasedIndex.ValueProcessor<String> { file, _ ->
            val yamlFile = psiManager.findFile(file) ?: return@ValueProcessor true
            val serviceDefinition = YAMLKeyValueFinder("services.$serviceId")
                .findIn(yamlFile)
                ?: return@ValueProcessor true

            resolveResults.add(PsiElementResolveResult(serviceDefinition.key ?: serviceDefinition))
            true
        }

        FileBasedIndex.getInstance()
            .processValues(SERVICES_INDEX_KEY, serviceId, null, processor, GlobalSearchScope.allScope(project))

        return resolveResults.toTypedArray()
    }
}
