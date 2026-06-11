package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.isAutowireServiceArgument
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Service id completion inside `#[Autowire(service: '...')]` (and the positional
 * `#[Autowire('...')]` form) on constructor parameters.
 */
class AutowireServiceCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project

        if (!Services.isSupportEnabled(project)) return

        val literal = leaf.parent as? StringLiteralExpression ?: return
        if (!literal.isAutowireServiceArgument()) return

        val serviceResultSet = completionResultSet.withPrefixMatcher(literal.contents)
        Services.allIds(project)
            .forEach { serviceId ->
                val serviceClass = Services.className(project, serviceId)
                var lookupElement = LookupElementBuilder.create(serviceId)

                if (!serviceClass.isNullOrBlank()) {
                    lookupElement = lookupElement.withTypeText(serviceClass, true)
                }

                serviceResultSet.addElement(lookupElement)
            }

        completionResultSet.stopHere()
    }
}
