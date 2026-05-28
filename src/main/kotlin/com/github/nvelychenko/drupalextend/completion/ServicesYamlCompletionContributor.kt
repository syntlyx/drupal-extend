package com.github.nvelychenko.drupalextend.completion

import com.github.nvelychenko.drupalextend.completion.providers.ServicesYamlCompletionProvider
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement

class ServicesYamlCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement(), ServicesYamlCompletionProvider())
    }
}
