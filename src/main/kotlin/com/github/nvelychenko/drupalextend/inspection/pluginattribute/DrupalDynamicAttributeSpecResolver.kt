package com.github.nvelychenko.drupalextend.inspection.pluginattribute

import com.github.nvelychenko.drupalextend.project.DrupalCoreVersion
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Resolves plugin annotations the static registry doesn't know — contrib modules and core
 * plugin types added after the registry snapshot — against the project itself. The conversion
 * is offered only when a matching `…\Attribute\<Name>` class exists in the project: that class
 * is the exact signal that the owning plugin manager understands attribute discovery, and
 * converting without it would silently break plugin discovery, so "no attribute class" means
 * "no quick fix" by design. Supported argument names come from the attribute constructor, so
 * an annotation key the constructor doesn't accept blocks the conversion instead of producing
 * code that doesn't compile.
 */
internal object DrupalDynamicAttributeSpecResolver {
    /** Doc-line-anchored so nested values like `label = @Translation("…")` don't match. */
    private val CANDIDATE_REGEX = Regex("""(?m)^\s*\*\s*@([A-Z][A-Za-z0-9_]*)\s*\(""")

    private val NESTED_ANNOTATION_NAMES = setOf("Translation", "PluralTranslation")

    private val PLUGIN_ATTRIBUTE_BASE_FQNS = setOf(
        "\\Drupal\\Component\\Plugin\\Attribute\\AttributeBase",
        "\\Drupal\\Component\\Plugin\\Attribute\\Plugin",
    )

    private const val PHP_ATTRIBUTE_FQN = "\\Attribute"

    private const val SUPER_CLASS_WALK_CAP = 8

    /** Existence of the attribute class is the gate; a core version can't say anything here. */
    private val ANY_DRUPAL_VERSION = DrupalCoreVersion(8, 0, 0)

    fun candidateNames(docText: String): List<String> {
        return CANDIDATE_REGEX.findAll(docText)
            .map { it.groupValues[1] }
            .distinct()
            .filter { it !in NESTED_ANNOTATION_NAMES && DrupalNestedAnnotationRegistry.findClassFqn(it) == null }
            .toList()
    }

    fun resolve(annotationName: String, project: Project): DrupalPluginAttributeSpec? {
        if (DumbService.isDumb(project)) return null

        val phpIndex = PhpIndex.getInstance(project)
        val candidates = phpIndex.getClassesByName(annotationName)
            .filter { it.fqn.endsWith("\\Attribute\\$annotationName") && it.isPluginAttributeClass() }
        val attributeClass = candidates.singleOrNull()
            ?: candidates.firstOrNull { phpIndex.hasSiblingAnnotationClass(it, annotationName) }
            ?: return null

        val constructor = attributeClass.constructorInHierarchy() ?: return null
        val supportedArguments = constructor.parameters
            .mapNotNull { parameter -> parameter.name.takeIf { it.isNotBlank() } }
            .toSet()
        if (supportedArguments.isEmpty()) return null

        return DrupalPluginAttributeSpec(
            annotationName = annotationName,
            attributeFqn = attributeClass.fqn,
            minimumDrupalVersion = ANY_DRUPAL_VERSION,
            supportedArguments = supportedArguments,
        )
    }

    /**
     * A plugin attribute either extends the component attribute bases or is a plain
     * `#[\Attribute]`-marked class somewhere up the hierarchy.
     */
    private fun PhpClass.isPluginAttributeClass(): Boolean {
        var current: PhpClass? = this
        var depth = 0
        while (current != null && depth < SUPER_CLASS_WALK_CAP) {
            ProgressManager.checkCanceled()
            if (current.fqn in PLUGIN_ATTRIBUTE_BASE_FQNS) return true
            if (current.attributes.any { it.fqn == PHP_ATTRIBUTE_FQN }) return true
            current = current.superClass
            depth++
        }

        return false
    }

    /**
     * Same-name attribute classes in several modules are ambiguous; the module whose
     * `…\Annotation\<Name>` counterpart exists is the one that owns the annotation being
     * converted.
     */
    private fun PhpIndex.hasSiblingAnnotationClass(attributeClass: PhpClass, annotationName: String): Boolean {
        val annotationFqn = attributeClass.fqn
            .removeSuffix("\\Attribute\\$annotationName") + "\\Annotation\\$annotationName"

        return getClassesByFQN(annotationFqn).isNotEmpty()
    }

    private fun PhpClass.constructorInHierarchy(): Method? {
        var current: PhpClass? = this
        var depth = 0
        while (current != null && depth < SUPER_CLASS_WALK_CAP) {
            ProgressManager.checkCanceled()
            current.ownConstructor?.let { return it }
            current = current.superClass
            depth++
        }

        return null
    }
}
