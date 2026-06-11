package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.reference.referenceProvider.InfoDependencyReferenceProvider
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.jetbrains.yaml.psi.YAMLScalar

/** References inside `*.info.yml` descriptors (module dependencies → their info.yml files). */
class InfoYamlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // dependencies:
        //   - node          → the bare module form
        //   - pathauto:path  → the project:module form (targets the module part)
        registrar.registerReferenceProvider(
            psiElement(YAMLScalar::class.java),
            InfoDependencyReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY
        )
    }
}
