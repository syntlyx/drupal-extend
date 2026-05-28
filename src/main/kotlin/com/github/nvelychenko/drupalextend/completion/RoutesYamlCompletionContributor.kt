package com.github.nvelychenko.drupalextend.completion

import com.github.nvelychenko.drupalextend.completion.providers.RoutesYamlCompletionProvider
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement

class RoutesYamlCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement(), RoutesYamlCompletionProvider())
    }
}
