package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.FIELD_PLUGIN_CONFIG_USAGE_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.FIELD_PLUGIN_DEFINITION_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.types.DrupalFieldPluginDefinition
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.unquoteString
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

    fun configUsageKey(kind: String, id: String): String {
        return "$kind|$id"
    }

    fun definition(phpClass: PhpClass): DrupalFieldPluginDefinition? {
        return attributeDefinition(phpClass)
            ?: annotationDefinition(phpClass)
    }

    fun referencesClass(project: Project, phpClass: PhpClass): Boolean {
        if (!project.drupalExtendSettings.isEnabled) return false

        val definition = definition(phpClass) ?: return false
        return isPluginIdUsed(project, definition.kind, definition.id) ||
                isDefaultPluginForUsedFieldType(project, definition)
    }

    private fun isPluginIdUsed(project: Project, kind: String, id: String): Boolean {
        return FileBasedIndex.getInstance()
            .getValues(FIELD_PLUGIN_CONFIG_USAGE_INDEX_KEY, configUsageKey(kind, id), GlobalSearchScope.allScope(project))
            .isNotEmpty()
    }

    private fun isDefaultPluginForUsedFieldType(project: Project, definition: DrupalFieldPluginDefinition): Boolean {
        if (definition.kind !in setOf(FIELD_WIDGET, FIELD_FORMATTER)) return false

        val index = FileBasedIndex.getInstance()
        return index.getAllProjectKeys(FIELD_PLUGIN_CONFIG_USAGE_INDEX_KEY, project)
            .asSequence()
            .filter { it.startsWith("$FIELD_TYPE|") }
            .map { it.substringAfter("|") }
            .mapNotNull { fieldTypeId ->
                index.getValue(FIELD_PLUGIN_DEFINITION_INDEX_KEY, definitionKey(FIELD_TYPE, fieldTypeId), project)
            }
            .any { fieldTypeDefinition ->
                when (definition.kind) {
                    FIELD_WIDGET -> fieldTypeDefinition.defaultWidget == definition.id
                    FIELD_FORMATTER -> fieldTypeDefinition.defaultFormatter == definition.id
                    else -> false
                }
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
                    fqn = phpClass.fqn,
                    defaultWidget = attribute.stringArgument("default_widget"),
                    defaultFormatter = attribute.stringArgument("default_formatter"),
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
                    fqn = phpClass.fqn,
                    defaultWidget = getPhpDocParameter(tagText, "default_widget"),
                    defaultFormatter = getPhpDocParameter(tagText, "default_formatter"),
                )
            }
    }

    private fun PhpAttribute.stringArgument(name: String, position: Int? = null): String? {
        val value = arguments.firstOrNull { it.name == name }?.argument?.value
            ?: position?.let { index ->
                arguments.filter { it.name == null }.getOrNull(index)?.argument?.value
            }
            ?: return null

        val unquotedValue = unquoteString(value)
        if (unquotedValue == value && !value.startsWith("'") && !value.startsWith("\"")) {
            return null
        }

        return unquotedValue.takeIf { it.isNotBlank() }
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
