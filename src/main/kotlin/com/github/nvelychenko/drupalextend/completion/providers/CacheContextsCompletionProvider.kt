package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.index.SERVICES_INDEX_KEY
import com.github.nvelychenko.drupalextend.util.Services
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Completes Drupal cache context ids inside:
 *
 *   $build['#cache']['contexts'][] = 'us|';
 *   $build = ['#cache' => ['contexts' => ['us|']]];
 *   $renderer->addCacheContexts(['us|']);
 *   $renderer->setCacheContexts(['us|']);
 *   $renderer->mergeCacheContexts(['us|']);
 *
 * Cache contexts are services named `cache_context.<id>`; the id list is derived by filtering the
 * already-cached services index keys by that prefix — no new index or caching layer.
 */
class CacheContextsCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val cacheContextServicePrefix = "cache_context."

    private val cacheContextMethodNames = setOf(
        "addCacheContexts",
        "setCacheContexts",
        "mergeCacheContexts",
    )

    public override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val leaf = parameters.originalPosition ?: return
        val project = leaf.project

        if (!Services.isSupportEnabled(project)) return

        val literal = leaf.parent as? StringLiteralExpression ?: return

        // Per-keystroke hot path: bail before touching the index unless the literal is in one of
        // the two shapes we serve.
        if (!isInCacheContextMethodArray(literal) && !isInRenderCacheContextsArray(literal)) return

        FileBasedIndex.getInstance()
            .getAllProjectKeys(SERVICES_INDEX_KEY, project)
            .asSequence()
            .filter { it.startsWith(cacheContextServicePrefix) && it.length > cacheContextServicePrefix.length }
            .map { it.substring(cacheContextServicePrefix.length) }
            .map(LookupElementBuilder::create)
            .forEach(result::addElement)
    }

    /**
     * `->addCacheContexts(['us|'])` etc. — string is an array value inside the parameter list of a
     * method whose NAME is one of the cache-context mutators (no resolve required).
     */
    private fun isInCacheContextMethodArray(literal: StringLiteralExpression): Boolean {
        val methodReference = PsiTreeUtil.getParentOfType(literal, MethodReference::class.java) ?: return false
        return methodReference.name in cacheContextMethodNames
    }

    /**
     * `'#cache' => ['contexts' => ['us|']]` — string is an array value whose enclosing
     * `'contexts'` hash element sits (directly) under a `'#cache'` hash element.
     */
    private fun isInRenderCacheContextsArray(literal: StringLiteralExpression): Boolean {
        val contextsHash = PsiTreeUtil.getParentOfType(literal, ArrayHashElement::class.java) ?: return false
        if ((contextsHash.key as? StringLiteralExpression)?.contents != "contexts") return false

        val cacheHash = PsiTreeUtil.getParentOfType(contextsHash, ArrayHashElement::class.java) ?: return false
        return (cacheHash.key as? StringLiteralExpression)?.contents == "#cache"
    }
}
