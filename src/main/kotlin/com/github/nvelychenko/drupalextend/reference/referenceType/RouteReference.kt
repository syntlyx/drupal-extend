package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.util.Routes
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

class RouteReference(
    element: PsiElement,
    private val routeName: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val project = element.project
    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

    override fun getCanonicalText(): String {
        return routeName
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (RoutingYaml.routeDefinitionFromKeyElement(element)?.keyText == routeName) return true
        return super.isReferenceTo(element)
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!Routes.isSupportEnabled(project)) return emptyArray()

        val resolveResults = mutableListOf<ResolveResult>()
        val processor = FileBasedIndex.ValueProcessor<com.github.nvelychenko.drupalextend.index.types.DrupalRoute> { file, _ ->
            val yamlFile = psiManager.findFile(file) as? YAMLFile ?: return@ValueProcessor true
            val routeDefinition = RoutingYaml.routeDefinition(yamlFile, routeName)
                ?: return@ValueProcessor true

            resolveResults.add(PsiElementResolveResult(routeDefinition.key ?: routeDefinition))
            true
        }

        FileBasedIndex.getInstance()
            .processValues(ROUTES_INDEX_KEY, routeName, null, processor, GlobalSearchScope.allScope(project))

        return resolveResults.toTypedArray()
    }
}
