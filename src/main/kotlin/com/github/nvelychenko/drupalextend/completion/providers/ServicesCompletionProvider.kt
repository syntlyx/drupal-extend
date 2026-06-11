package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Configs
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.getServiceMethodReference
import com.github.nvelychenko.drupalextend.util.isContainerGetCall
import com.github.nvelychenko.drupalextend.util.isDrupalServiceCall
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ServicesCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project

        if (!Services.isSupportEnabled(project)) return

        val element = PsiTreeUtil.getParentOfType(leaf, StringLiteralExpression::class.java, false) ?: return
        val methodReference = element.getServiceMethodReference() ?: return
        if (!methodReference.isDrupalServiceCall() && !methodReference.isContainerGetCall()) return

        val serviceResultSet = completionResultSet.withPrefixMatcher(element.contents)
        Services.allIds(project)
            .forEach {
                val serviceClass = Services.className(project, it)
                var lookupElement = LookupElementBuilder.create(it)

                if (!serviceClass.isNullOrBlank()) {
                    lookupElement = lookupElement.withTypeText(serviceClass, true)
                }

                serviceResultSet.addElement(lookupElement)
            }

        // A receiver can satisfy both the container heuristic and the config-factory heuristic
        // (e.g. `$configFactoryContainer->get('…')`). stopHere() would silently starve the
        // config-name provider registered after this one, so let the result set stay open then.
        if (!Configs.isConfigNameCall(methodReference)) {
            completionResultSet.stopHere()
        }
    }
}
