package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.util.DrupalConfigUsagePlugins
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * A plugin id in config YAML (`plugin_id: nll_core_verify_address`, a views handler
 * `plugin_id`, a block placement `plugin`, …) resolving to the plugin class. Soft — an id the
 * project doesn't define (core/contrib plugin outside the index, disabled module) must not be
 * flagged unresolved.
 */
class DrupalPluginIdReference(
    scalar: YAMLScalar,
    private val kind: String,
    private val pluginId: String,
) : PsiReferenceBase<YAMLScalar>(scalar, true) {
    override fun resolve(): PsiElement? {
        val project = element.project
        if (DumbService.isDumb(project)) return null

        return DrupalConfigUsagePlugins.findPluginClass(project, kind, pluginId)
    }

    /**
     * The scalar is the plugin *id*, not the class name — renaming the class must never
     * rewrite it (the id is derived from the attribute, which the rename doesn't change).
     */
    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element
}
