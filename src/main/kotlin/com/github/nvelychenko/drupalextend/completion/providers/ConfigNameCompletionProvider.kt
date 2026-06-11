package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.Configs
import com.github.nvelychenko.drupalextend.util.getServiceMethodReference
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ConfigNameCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project

        if (!Configs.isSupportEnabled(project)) return

        val element = PsiTreeUtil.getParentOfType(leaf, StringLiteralExpression::class.java, false) ?: return
        val methodReference = element.getServiceMethodReference() ?: return
        if (!Configs.isConfigNameCall(methodReference)) return

        val configResultSet = completionResultSet.withPrefixMatcher(element.contents)
        Configs.allNames(project)
            .forEach { configResultSet.addElement(LookupElementBuilder.create(it)) }

        completionResultSet.stopHere()
    }
}
