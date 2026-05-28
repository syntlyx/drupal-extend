package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.reference.referenceProvider.RoutesYamlReferenceProvider
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.jetbrains.yaml.psi.YAMLScalar

class RoutesYamlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(YAMLScalar::class.java),
            RoutesYamlReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY
        )
    }
}
