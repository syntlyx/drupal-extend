package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.index.*
import com.intellij.util.indexing.FileBasedIndex

fun clearPluginIndexes() {
    arrayOf(
        CONFIG_ENTITY_INDEX_KEY,
        CONTENT_ENTITY_INDEX_KEY,
        CONTENT_ENTITY_FQN_INDEX_KEY,
        FIELDS_INDEX_KEY,
        FIELD_PLUGIN_CONFIG_USAGE_INDEX_KEY,
        FIELD_PLUGIN_DEFINITION_INDEX_KEY,
        FIELD_TYPE_INDEX_KEY,
        RENDER_ELEMENT_INDEX_KEY,
        THEME_INDEX_KEY,
        LIBRARIES_INDEX_KEY,
        PERMISSIONS_INDEX_KEY,
        SERVICES_INDEX_KEY,
        SERVICE_FACTORY_INDEX_KEY,
        ROUTES_INDEX_KEY,
        LINKS_INDEX_KEY,
    ).forEach { FileBasedIndex.getInstance().requestRebuild(it) }
}
