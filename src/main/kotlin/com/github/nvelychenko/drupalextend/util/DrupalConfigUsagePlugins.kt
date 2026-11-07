package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.PLUGIN_CONFIG_USAGE_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.PLUGIN_DEFINITION_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.PluginConfigUsageIndex
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Plugin types whose classes are reachable at runtime only once project config references
 * their plugin id: REST resources (`rest.resource.*`), blocks (`block.block.*` placements and
 * Layout Builder components), actions (`system.action.*`), image effects (`image.style.*`),
 * media sources (`media.type.*`), text format filters (`filter.format.*`), layouts
 * (Layout Builder `layout_id`), and views handler/display plugins (`views.view.*`).
 *
 * [PluginConfigUsageIndex] records the config side; here the class side (attribute or legacy
 * annotation) is matched against it, so an enabled plugin is implicitly used while a plugin
 * no config references keeps its "unused" warning. Field plugins stay in [DrupalFieldPlugins] —
 * they need extra reachability rules (defaults, `field_types`) beyond a plain id lookup.
 */
object DrupalConfigUsagePlugins {
    const val REST_RESOURCE = "rest_resource"
    const val BLOCK = "block"
    const val ACTION = "action"
    const val IMAGE_EFFECT = "image_effect"
    const val MEDIA_SOURCE = "media_source"
    const val FILTER = "filter"
    const val LAYOUT = "layout"
    const val VIEWS_ACCESS = "views_access"
    const val VIEWS_AREA = "views_area"
    const val VIEWS_ARGUMENT = "views_argument"
    const val VIEWS_ARGUMENT_DEFAULT = "views_argument_default"
    const val VIEWS_CACHE = "views_cache"
    const val VIEWS_DISPLAY = "views_display"
    const val VIEWS_EXPOSED_FORM = "views_exposed_form"
    const val VIEWS_FIELD = "views_field"
    const val VIEWS_FILTER = "views_filter"
    const val VIEWS_PAGER = "views_pager"
    const val VIEWS_QUERY = "views_query"
    const val VIEWS_RELATIONSHIP = "views_relationship"
    const val VIEWS_ROW = "views_row"
    const val VIEWS_SORT = "views_sort"
    const val VIEWS_STYLE = "views_style"

    private data class PluginType(val kind: String, val attributeFqn: String, val annotationName: String)

    private val types = listOf(
        PluginType(REST_RESOURCE, "\\Drupal\\rest\\Attribute\\RestResource", "RestResource"),
        PluginType(BLOCK, "\\Drupal\\Core\\Block\\Attribute\\Block", "Block"),
        PluginType(ACTION, "\\Drupal\\Core\\Action\\Attribute\\Action", "Action"),
        PluginType(IMAGE_EFFECT, "\\Drupal\\image\\Attribute\\ImageEffect", "ImageEffect"),
        PluginType(MEDIA_SOURCE, "\\Drupal\\media\\Attribute\\MediaSource", "MediaSource"),
        PluginType(FILTER, "\\Drupal\\filter\\Attribute\\Filter", "Filter"),
        PluginType(LAYOUT, "\\Drupal\\Core\\Layout\\Attribute\\Layout", "Layout"),
        PluginType(VIEWS_ACCESS, "\\Drupal\\views\\Attribute\\ViewsAccess", "ViewsAccess"),
        PluginType(VIEWS_AREA, "\\Drupal\\views\\Attribute\\ViewsArea", "ViewsArea"),
        PluginType(VIEWS_ARGUMENT, "\\Drupal\\views\\Attribute\\ViewsArgument", "ViewsArgument"),
        PluginType(VIEWS_ARGUMENT_DEFAULT, "\\Drupal\\views\\Attribute\\ViewsArgumentDefault", "ViewsArgumentDefault"),
        PluginType(VIEWS_CACHE, "\\Drupal\\views\\Attribute\\ViewsCache", "ViewsCache"),
        PluginType(VIEWS_DISPLAY, "\\Drupal\\views\\Attribute\\ViewsDisplay", "ViewsDisplay"),
        PluginType(VIEWS_EXPOSED_FORM, "\\Drupal\\views\\Attribute\\ViewsExposedForm", "ViewsExposedForm"),
        PluginType(VIEWS_FIELD, "\\Drupal\\views\\Attribute\\ViewsField", "ViewsField"),
        PluginType(VIEWS_FILTER, "\\Drupal\\views\\Attribute\\ViewsFilter", "ViewsFilter"),
        PluginType(VIEWS_PAGER, "\\Drupal\\views\\Attribute\\ViewsPager", "ViewsPager"),
        PluginType(VIEWS_QUERY, "\\Drupal\\views\\Attribute\\ViewsQuery", "ViewsQuery"),
        PluginType(VIEWS_RELATIONSHIP, "\\Drupal\\views\\Attribute\\ViewsRelationship", "ViewsRelationship"),
        PluginType(VIEWS_ROW, "\\Drupal\\views\\Attribute\\ViewsRow", "ViewsRow"),
        PluginType(VIEWS_SORT, "\\Drupal\\views\\Attribute\\ViewsSort", "ViewsSort"),
        PluginType(VIEWS_STYLE, "\\Drupal\\views\\Attribute\\ViewsStyle", "ViewsStyle"),
    )

    private val typesByAttributeFqn = types.associateBy { it.attributeFqn }

    fun referencesClass(project: Project, phpClass: PhpClass): Boolean {
        if (!project.drupalExtendSettings.isEnabled) return false

        val (kind, id) = pluginKindAndId(phpClass) ?: return false

        return FileBasedIndex.getInstance()
            .getValues(PLUGIN_CONFIG_USAGE_INDEX_KEY, PluginConfigUsageIndex.usageKey(kind, id), GlobalSearchScope.allScope(project))
            .isNotEmpty()
    }

    /**
     * Resolves a plugin id from config YAML back to its class via [PLUGIN_DEFINITION_INDEX_KEY];
     * a derived id (`my_block:derivative`) falls back to its deriver base class.
     */
    fun findPluginClass(project: Project, kind: String, pluginId: String): PhpClass? {
        val index = FileBasedIndex.getInstance()
        val fqn = index.getValue(PLUGIN_DEFINITION_INDEX_KEY, PluginConfigUsageIndex.usageKey(kind, pluginId), project)
            ?: pluginId.substringBefore(':')
                .takeIf { it != pluginId && it.isNotBlank() }
                ?.let { baseId -> index.getValue(PLUGIN_DEFINITION_INDEX_KEY, PluginConfigUsageIndex.usageKey(kind, baseId), project) }
            ?: return null

        return PhpIndex.getInstance(project).getClassesByFQN(fqn).firstOrNull()
    }

    internal fun pluginKindAndId(phpClass: PhpClass): Pair<String, String>? {
        return attributeKindAndId(phpClass) ?: annotationKindAndId(phpClass)
    }

    private fun attributeKindAndId(phpClass: PhpClass): Pair<String, String>? {
        for (attribute in phpClass.attributes) {
            val type = typesByAttributeFqn[attribute.fqn ?: continue] ?: continue
            val id = attribute.stringArgument("id", 0) ?: continue
            return type.kind to id
        }
        return null
    }

    private fun annotationKindAndId(phpClass: PhpClass): Pair<String, String>? {
        val docComment = phpClass.docComment ?: return null

        return types.firstNotNullOfOrNull { type ->
            val tagText = docComment.getTagElementsByName("@${type.annotationName}")
                .firstOrNull()
                ?.text
                ?: return@firstNotNullOfOrNull null
            annotationId(tagText, type.annotationName)?.let { type.kind to it }
        }
    }

    /**
     * Views annotations conventionally carry the plugin id as a single positional string —
     * `@ViewsField("node_bulk_form")` — instead of the `id = "…"` form the other types use.
     */
    private fun annotationId(tagText: String, annotationName: String): String? {
        getPhpDocParameter(tagText, "id")?.let { return it }

        return Regex("^@${Regex.escape(annotationName)}\\(\\s*([\"'])([^\"']+)\\1\\s*\\)")
            .find(tagText.trim())
            ?.groupValues
            ?.get(2)
    }
}
