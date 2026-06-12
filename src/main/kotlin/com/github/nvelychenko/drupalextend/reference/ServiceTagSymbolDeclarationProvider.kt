package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Declares the tag name in `tags: - { name: '…' }` as a symbol *declaration*. A YAML value
 * scalar is not a named element, so without this Ctrl+Click reports "cannot find declaration to
 * go to" — the goto-or-usages handler only switches to its usages branch when the caret sits on
 * a declared symbol (service definition keys get this for free by being named elements). With
 * the declaration in place, Ctrl+Click runs Show Usages through the registered find-usages
 * handler and references search, exactly like service ids.
 */
class ServiceTagSymbolDeclarationProvider : PsiSymbolDeclarationProvider {
    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
        val scalar = element as? YAMLScalar ?: return emptyList()
        if (!Services.isSupportEnabled(scalar.project)) return emptyList()
        if (ServicesYaml.tagNameDeclarationScalar(scalar) != scalar) return emptyList()

        return listOf(ServiceTagDeclaration(scalar))
    }

    private class ServiceTagDeclaration(private val scalar: YAMLScalar) : PsiSymbolDeclaration {
        override fun getDeclaringElement(): PsiElement = scalar

        override fun getRangeInDeclaringElement(): TextRange = TextRange(0, scalar.textLength)

        override fun getSymbol(): Symbol = PsiSymbolService.getInstance().asSymbol(scalar)
    }
}
