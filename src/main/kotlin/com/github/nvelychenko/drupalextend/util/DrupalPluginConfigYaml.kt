package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.keyPath
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * The single home for "where does a config YAML carry a plugin id" — file-name patterns and
 * key paths. Both the config-usage index and the plugin-id references derive positions from
 * here, so unused-detection and navigation can't drift apart.
 */
internal object DrupalPluginConfigYaml {
    fun isRestResourceConfig(fileName: String): Boolean {
        return fileName.startsWith("rest.resource.") && fileName.endsWith(".yml")
    }

    fun isBlockPlacementConfig(fileName: String): Boolean {
        return fileName.startsWith("block.block.") && fileName.endsWith(".yml")
    }

    fun isActionConfig(fileName: String): Boolean {
        return fileName.startsWith("system.action.") && fileName.endsWith(".yml")
    }

    fun isMediaTypeConfig(fileName: String): Boolean {
        return fileName.startsWith("media.type.") && fileName.endsWith(".yml")
    }

    fun isImageStyleConfig(fileName: String): Boolean {
        return fileName.startsWith("image.style.") && fileName.endsWith(".yml")
    }

    fun isFilterFormatConfig(fileName: String): Boolean {
        return fileName.startsWith("filter.format.") && fileName.endsWith(".yml")
    }

    fun isViewsConfig(fileName: String): Boolean {
        return fileName.startsWith("views.view.") && fileName.endsWith(".yml")
    }

    fun isEntityViewDisplayConfig(fileName: String): Boolean {
        return (fileName.startsWith("core.entity_view_display.") || fileName.startsWith("entity_view_display.")) &&
                fileName.endsWith(".yml")
    }

    /**
     * The plugin kind whose id sits at this key-value position, or null when the position
     * isn't a plugin id. Field plugin positions (widget/formatter `type` keys) are not
     * covered here — they live in [DrupalFieldPlugins]' domain.
     */
    fun pluginKindAt(fileName: String, keyValue: YAMLKeyValue): String? {
        val keyPath = keyValue.keyPath
        val segments = keyPath.split(".")

        return when {
            isRestResourceConfig(fileName) ->
                DrupalConfigUsagePlugins.REST_RESOURCE.takeIf { keyPath == "plugin_id" }
            isBlockPlacementConfig(fileName) ->
                DrupalConfigUsagePlugins.BLOCK.takeIf { keyPath == "plugin" }
            isActionConfig(fileName) ->
                DrupalConfigUsagePlugins.ACTION.takeIf { keyPath == "plugin" }
            isMediaTypeConfig(fileName) ->
                DrupalConfigUsagePlugins.MEDIA_SOURCE.takeIf { keyPath == "source" }
            isImageStyleConfig(fileName) ->
                DrupalConfigUsagePlugins.IMAGE_EFFECT.takeIf { isNestedIdKey(segments, "effects") }
            isFilterFormatConfig(fileName) ->
                DrupalConfigUsagePlugins.FILTER.takeIf { isNestedIdKey(segments, "filters") }
            isViewsConfig(fileName) -> viewsPluginKindAt(segments)
            isEntityViewDisplayConfig(fileName) -> layoutBuilderPluginKindAt(keyPath)
            else -> null
        }
    }

    /**
     * `views.view.*` plugin positions: handler plugin ids at
     * `display_options.<section>.<name>.plugin_id`, display-level plugins at
     * `display_options.<section>.type`, the display plugin at `display.<display>.display_plugin`,
     * and contextual-filter default-argument plugins under `default_argument_type`.
     */
    fun viewsPluginKindAt(segments: List<String>): String? {
        return when {
            isViewsHandlerPluginIdKey(segments) -> VIEWS_HANDLER_SECTION_KINDS.getValue(segments[3])
            isViewsDisplaySectionTypeKey(segments) -> VIEWS_DISPLAY_SECTION_KINDS.getValue(segments[3])
            isViewsDisplayPluginKey(segments) -> DrupalConfigUsagePlugins.VIEWS_DISPLAY
            segments.last() == "default_argument_type" -> DrupalConfigUsagePlugins.VIEWS_ARGUMENT_DEFAULT
            else -> null
        }
    }

    /**
     * Layout Builder positions inside an entity view display (sequence items don't contribute
     * keyPath segments, so tails are matched): every component carries its block plugin id at
     * `…configuration.id`, each section its layout at `…sections[].layout_id`.
     */
    fun layoutBuilderPluginKindAt(keyPath: String): String? {
        if (!keyPath.contains("layout_builder.")) return null

        return when {
            keyPath.endsWith(".configuration.id") -> DrupalConfigUsagePlugins.BLOCK
            keyPath.endsWith(".layout_id") -> DrupalConfigUsagePlugins.LAYOUT
            else -> null
        }
    }

    private fun isNestedIdKey(segments: List<String>, rootKey: String): Boolean {
        return segments.size == 3 && segments[0] == rootKey && segments.last() == "id"
    }

    private fun isViewsHandlerPluginIdKey(segments: List<String>): Boolean {
        return segments.size == 6 &&
                segments[2] == "display_options" &&
                segments.last() == "plugin_id" &&
                segments[3] in VIEWS_HANDLER_SECTION_KINDS
    }

    private fun isViewsDisplaySectionTypeKey(segments: List<String>): Boolean {
        return segments.size == 5 &&
                segments[2] == "display_options" &&
                segments.last() == "type" &&
                segments[3] in VIEWS_DISPLAY_SECTION_KINDS
    }

    private fun isViewsDisplayPluginKey(segments: List<String>): Boolean {
        return segments.size == 3 && segments[0] == "display" && segments.last() == "display_plugin"
    }
}

private val VIEWS_HANDLER_SECTION_KINDS = mapOf(
    "fields" to DrupalConfigUsagePlugins.VIEWS_FIELD,
    "filters" to DrupalConfigUsagePlugins.VIEWS_FILTER,
    "sorts" to DrupalConfigUsagePlugins.VIEWS_SORT,
    "arguments" to DrupalConfigUsagePlugins.VIEWS_ARGUMENT,
    "relationships" to DrupalConfigUsagePlugins.VIEWS_RELATIONSHIP,
    "header" to DrupalConfigUsagePlugins.VIEWS_AREA,
    "footer" to DrupalConfigUsagePlugins.VIEWS_AREA,
    "empty" to DrupalConfigUsagePlugins.VIEWS_AREA,
)

private val VIEWS_DISPLAY_SECTION_KINDS = mapOf(
    "style" to DrupalConfigUsagePlugins.VIEWS_STYLE,
    "row" to DrupalConfigUsagePlugins.VIEWS_ROW,
    "pager" to DrupalConfigUsagePlugins.VIEWS_PAGER,
    "cache" to DrupalConfigUsagePlugins.VIEWS_CACHE,
    "access" to DrupalConfigUsagePlugins.VIEWS_ACCESS,
    "exposed_form" to DrupalConfigUsagePlugins.VIEWS_EXPOSED_FORM,
    "query" to DrupalConfigUsagePlugins.VIEWS_QUERY,
)
