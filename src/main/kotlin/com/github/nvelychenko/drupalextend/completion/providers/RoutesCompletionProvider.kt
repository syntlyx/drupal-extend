package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.getRouteMethodReference
import com.github.nvelychenko.drupalextend.util.isRouteCall
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class RoutesCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project

        if (!Routes.isSupportEnabled(project)) return

        val element = PsiTreeUtil.getParentOfType(leaf, StringLiteralExpression::class.java, false) ?: return
        val methodReference = element.getRouteMethodReference() ?: return
        if (!methodReference.isRouteCall(project)) return

        val routeResultSet = completionResultSet.withPrefixMatcher(element.contents)
        Routes.allNames(project)
            .forEach { routeName ->
                val route = Routes.definition(project, routeName)
                var lookupElement = LookupElementBuilder.create(routeName)

                if (!route?.path.isNullOrBlank()) {
                    lookupElement = lookupElement.withTypeText(route.path, true)
                }

                routeResultSet.addElement(lookupElement)
            }

        completionResultSet.stopHere()
    }
}
