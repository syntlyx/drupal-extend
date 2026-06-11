package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.reference.referenceType.ModuleReference
import com.github.nvelychenko.drupalextend.util.Modules
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Navigation from a `dependencies:` entry in a `*.info.yml` descriptor to the dependency's own
 * `*.info.yml`. Handles both the bare `module` form and the `project:module` form, in which the
 * reference targets only the part after the colon.
 *
 * Registered on a broad `YAMLScalar` pattern; bails O(1) when the scalar is not a dependency
 * sequence item in an info.yml.
 */
class InfoDependencyReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val project = element.project

        if (!Modules.isSupportEnabled(project)) return PsiReference.EMPTY_ARRAY

        val fileName = element.containingFile?.virtualFile?.name ?: element.containingFile?.name
        if (fileName?.endsWith(".info.yml") != true) return PsiReference.EMPTY_ARRAY

        val scalar = element as? YAMLScalar ?: return PsiReference.EMPTY_ARRAY
        if (!isDependencyItem(scalar)) return PsiReference.EMPTY_ARRAY

        val entry = scalar.textValue.trim()
        if (entry.isEmpty()) return PsiReference.EMPTY_ARRAY

        // `project:module` → the machine name is the part after the colon; the bare form is the
        // whole entry. The reference range covers only the module portion of the scalar text.
        val colonIndex = entry.indexOf(':')
        val machineName = if (colonIndex >= 0) entry.substring(colonIndex + 1) else entry
        if (machineName.isEmpty()) return PsiReference.EMPTY_ARRAY

        val moduleStartInEntry = if (colonIndex >= 0) colonIndex + 1 else 0
        val entryStartInScalar = scalar.text.indexOf(entry).takeIf { it >= 0 } ?: 0
        val rangeStart = entryStartInScalar + moduleStartInEntry
        val range = TextRange(rangeStart, rangeStart + machineName.length)

        return arrayOf(ModuleReference(scalar, machineName, range))
    }

    private fun isDependencyItem(scalar: YAMLScalar): Boolean {
        val sequenceItem = scalar.parent as? YAMLSequenceItem ?: return false
        if (sequenceItem.value != scalar) return false

        val sequence = sequenceItem.parent as? YAMLSequence ?: return false
        val keyValue = sequence.parent as? YAMLKeyValue ?: return false
        return keyValue.keyText == "dependencies"
    }
}
