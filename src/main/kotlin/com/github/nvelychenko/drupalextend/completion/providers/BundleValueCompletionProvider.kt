package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Bundles
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Bundle id completion in bundle-value positions:
 * `entityQuery('node')->condition('type', '<caret>')`, `Node::create(['type' => '<caret>'])`,
 * `loadByProperties(['type' => '<caret>'])`, and `drupalCreateNode(['type' => '<caret>'])`.
 */
class BundleValueCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project
        if (DumbService.isDumb(project)) return

        val literal = leaf.parent as? StringLiteralExpression ?: return
        val entityTypeId = Bundles.bundleValueEntityType(literal) ?: return

        val bundleResultSet = completionResultSet.withPrefixMatcher(literal.contents)
        Bundles.bundleIds(project, entityTypeId).forEach { bundleId ->
            bundleResultSet.addElement(
                LookupElementBuilder.create(bundleId)
                    .withTypeText(Bundles.bundleConfigName(entityTypeId, bundleId), true)
            )
        }
    }
}
