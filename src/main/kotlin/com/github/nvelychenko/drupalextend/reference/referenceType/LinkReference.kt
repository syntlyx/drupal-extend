package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.util.Links
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.yaml.psi.YAMLFile

class LinkReference(
    element: PsiElement,
    private val linkId: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project
    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

    override fun getCanonicalText(): String {
        return linkId
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (RoutingYaml.linkDefinitionFromKeyElement(element)?.keyText == linkId) return true
        return super.isReferenceTo(element)
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Links.isSupportEnabled(project)) return emptyArray()

        val resolveResults = mutableListOf<ResolveResult>()
        val processor = FileBasedIndex.ValueProcessor<String> { file, _ ->
            val yamlFile = psiManager.findFile(file) as? YAMLFile ?: return@ValueProcessor true
            val linkDefinition = RoutingYaml.linkDefinition(yamlFile, linkId)
                ?: return@ValueProcessor true

            resolveResults.add(PsiElementResolveResult(linkDefinition.key ?: linkDefinition))
            true
        }

        FileBasedIndex.getInstance()
            .processValues(LINKS_INDEX_KEY, linkId, null, processor, GlobalSearchScope.allScope(project))

        return resolveResults.toTypedArray()
    }
}
