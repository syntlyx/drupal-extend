package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.reference.referenceProvider.ServicesYamlReferenceProvider
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.jetbrains.yaml.psi.YAMLScalar

class ServicesYamlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            psiElement(YAMLScalar::class.java),
            ServicesYamlReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY
        )
    }
}
