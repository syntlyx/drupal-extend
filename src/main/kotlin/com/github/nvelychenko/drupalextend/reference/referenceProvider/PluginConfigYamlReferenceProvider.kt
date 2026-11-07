package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.DrupalPluginIdReference
import com.github.nvelychenko.drupalextend.util.DrupalPluginConfigYaml
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Attaches [DrupalPluginIdReference] to plugin-id positions in config YAML (the positions
 * [DrupalPluginConfigYaml] knows). Pure-PSI shape checks run before any service lookup —
 * this provider is registered on a broad scalar pattern.
 */
class PluginConfigYamlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val scalar = element as? YAMLScalar ?: return PsiReference.EMPTY_ARRAY
        val keyValue = scalar.parent as? YAMLKeyValue ?: return PsiReference.EMPTY_ARRAY
        if (keyValue.value != scalar) return PsiReference.EMPTY_ARRAY

        val fileName = scalar.containingFile?.name ?: return PsiReference.EMPTY_ARRAY
        val kind = DrupalPluginConfigYaml.pluginKindAt(fileName, keyValue) ?: return PsiReference.EMPTY_ARRAY
        val pluginId = scalar.textValue.trim().takeIf { it.isNotBlank() } ?: return PsiReference.EMPTY_ARRAY

        if (!scalar.project.drupalExtendSettings.isEnabled) return PsiReference.EMPTY_ARRAY

        return arrayOf(DrupalPluginIdReference(scalar, kind, pluginId))
    }
}
