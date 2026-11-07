package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.FIELD_PLUGIN_DEFINITION_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.PLUGIN_CONFIG_USAGE_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.PluginConfigUsageIndex
import com.github.nvelychenko.drupalextend.index.types.DrupalFieldPluginDefinition
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.PhpClass

object DrupalFieldPlugins {
    const val FIELD_TYPE = "field_type"
    const val FIELD_WIDGET = "field_widget"
    const val FIELD_FORMATTER = "field_formatter"

    private const val FIELD_TYPE_ATTRIBUTE = "\\Drupal\\Core\\Field\\Attribute\\FieldType"
    private const val FIELD_WIDGET_ATTRIBUTE = "\\Drupal\\Core\\Field\\Attribute\\FieldWidget"
    private const val FIELD_FORMATTER_ATTRIBUTE = "\\Drupal\\Core\\Field\\Attribute\\FieldFormatter"

    fun definitionKey(kind: String, id: String): String {
        return "$kind|$id"
    }

    fun definition(phpClass: PhpClass): DrupalFieldPluginDefinition? {
        return attributeDefinition(phpClass)
            ?: annotationDefinition(phpClass)
    }

    fun referencesClass(project: Project, phpClass: PhpClass): Boolean {
        if (!project.drupalExtendSettings.isEnabled) return false

        val definition = definition(phpClass) ?: return false

        // A field type is discovered by the plugin manager and selectable in the Field UI
        // the moment it exists — no code or config reference is ever required for it to be
        // reachable at runtime, so it is never dead code.
        if (definition.kind == FIELD_TYPE) return true

        return isPluginIdUsed(project, definition.kind, definition.id) ||
                isDefaultPluginForKnownFieldType(project, definition) ||
                supportsExistingFieldType(project, definition)
    }

    private fun isPluginIdUsed(project: Project, kind: String, id: String): Boolean {
        return FileBasedIndex.getInstance()
            .getValues(PLUGIN_CONFIG_USAGE_INDEX_KEY, PluginConfigUsageIndex.usageKey(kind, id), GlobalSearchScope.allScope(project))
            .isNotEmpty()
    }

    /**
     * The `default_widget`/`default_formatter` of a field type kick in automatically whenever
     * a field of that type is added (in the UI or in code), so the defaults of every field type
     * defined in the project are reachable — "defined", not merely "used in config": the
     * definition index only ever answers for project-indexed types anyway.
     */
    private fun isDefaultPluginForKnownFieldType(project: Project, definition: DrupalFieldPluginDefinition): Boolean {
        if (definition.kind !in setOf(FIELD_WIDGET, FIELD_FORMATTER)) return false

        val index = FileBasedIndex.getInstance()
        return index.getAllProjectKeys(FIELD_PLUGIN_DEFINITION_INDEX_KEY, project)
            .asSequence()
            .filter { it.startsWith("$FIELD_TYPE|") }
            .mapNotNull { definitionIndexKey ->
                index.getValue(FIELD_PLUGIN_DEFINITION_INDEX_KEY, definitionIndexKey, project)
            }
            .any { fieldTypeDefinition ->
                when (definition.kind) {
                    FIELD_WIDGET -> fieldTypeDefinition.defaultWidget == definition.id
                    FIELD_FORMATTER -> fieldTypeDefinition.defaultFormatter == definition.id
                    else -> false
                }
            }
    }

    /**
     * A widget/formatter whose `field_types` list names a field type that exists in the
     * project (or is used by config) is selectable in the Field UI and via form/view
     * display settings without any code or config reference — it is not dead code.
     * A plugin whose declared field types match nothing keeps its "unused" warning.
     */
    private fun supportsExistingFieldType(project: Project, definition: DrupalFieldPluginDefinition): Boolean {
        if (definition.kind != FIELD_WIDGET && definition.kind != FIELD_FORMATTER) return false

        val index = FileBasedIndex.getInstance()
        return definition.fieldTypes.any { fieldTypeId ->
            index.getValue(FIELD_PLUGIN_DEFINITION_INDEX_KEY, definitionKey(FIELD_TYPE, fieldTypeId), project) != null ||
                    isPluginIdUsed(project, FIELD_TYPE, fieldTypeId)
        }
    }

    private fun attributeDefinition(phpClass: PhpClass): DrupalFieldPluginDefinition? {
        return fieldPluginAttributes()
            .firstNotNullOfOrNull { (kind, attributeFqn) ->
                val attribute = phpClass.attributes.find { it.fqn == attributeFqn } ?: return@firstNotNullOfOrNull null
                val id = attribute.stringArgument("id", 0) ?: return@firstNotNullOfOrNull null
                DrupalFieldPluginDefinition(
                    kind = kind,
                    id = id,
                    defaultWidget = attribute.stringArgument("default_widget"),
                    defaultFormatter = attribute.stringArgument("default_formatter"),
                    fieldTypes = attribute.stringListArgument("field_types"),
                )
            }
    }

    private fun annotationDefinition(phpClass: PhpClass): DrupalFieldPluginDefinition? {
        val docComment = phpClass.docComment ?: return null

        return fieldPluginAnnotations()
            .firstNotNullOfOrNull { (kind, annotationName) ->
                val tagText = docComment.getTagElementsByName("@$annotationName")
                    .firstOrNull()
                    ?.text
                    ?: return@firstNotNullOfOrNull null
                val id = getPhpDocParameter(tagText, "id") ?: return@firstNotNullOfOrNull null
                DrupalFieldPluginDefinition(
                    kind = kind,
                    id = id,
                    defaultWidget = getPhpDocParameter(tagText, "default_widget"),
                    defaultFormatter = getPhpDocParameter(tagText, "default_formatter"),
                    fieldTypes = annotationFieldTypes(tagText),
                )
            }
    }

    /**
     * Extracts `field_types = { "a", "b" }` ids from an annotation tag's text.
     */
    private fun annotationFieldTypes(tagText: String): List<String> {
        val body = ANNOTATION_FIELD_TYPES_REGEX.find(tagText)?.groupValues?.get(1) ?: return emptyList()
        return QUOTED_STRING_REGEX.findAll(body)
            .map { it.groupValues[2] }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * Extracts the string items of an array attribute argument, e.g.
     * `field_types: ['loan_to_value_rate']`. Works on the attribute's source text so it
     * stays index-safe (no resolve), covering both quote styles.
     */
    private fun PhpAttribute.stringListArgument(name: String): List<String> {
        val body = Regex("(?<![\\w'\"])${Regex.escape(name)}\\s*:\\s*\\[([^\\]]*)]").find(text)
            ?.groupValues?.get(1)
            ?: return emptyList()

        return QUOTED_STRING_REGEX.findAll(body)
            .map { it.groupValues[2] }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun fieldPluginAttributes(): List<Pair<String, String>> {
        return listOf(
            FIELD_TYPE to FIELD_TYPE_ATTRIBUTE,
            FIELD_WIDGET to FIELD_WIDGET_ATTRIBUTE,
            FIELD_FORMATTER to FIELD_FORMATTER_ATTRIBUTE,
        )
    }

    private fun fieldPluginAnnotations(): List<Pair<String, String>> {
        return listOf(
            FIELD_TYPE to "FieldType",
            FIELD_WIDGET to "FieldWidget",
            FIELD_FORMATTER to "FieldFormatter",
        )
    }
}

private val ANNOTATION_FIELD_TYPES_REGEX = Regex("(?<![\\w\"])field_types\\s*=\\s*\\{([^}]*)}")
private val QUOTED_STRING_REGEX = Regex("(['\"])([^'\"]+)\\1")
