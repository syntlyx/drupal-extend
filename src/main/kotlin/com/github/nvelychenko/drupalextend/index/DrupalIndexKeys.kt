package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.index.types.ContentEntity
import com.github.nvelychenko.drupalextend.index.types.DrupalConfigObject
import com.github.nvelychenko.drupalextend.index.types.DrupalContentEntity
import com.github.nvelychenko.drupalextend.index.types.DrupalField
import com.github.nvelychenko.drupalextend.index.types.DrupalFieldPluginDefinition
import com.github.nvelychenko.drupalextend.index.types.DrupalFieldType
import com.github.nvelychenko.drupalextend.index.types.DrupalRoute
import com.github.nvelychenko.drupalextend.index.types.DrupalServiceFactory
import com.github.nvelychenko.drupalextend.index.types.DrupalServiceTag
import com.github.nvelychenko.drupalextend.index.types.DrupalTheme
import com.github.nvelychenko.drupalextend.index.types.RenderElementType
import com.intellij.util.indexing.ID

val CONFIG_ENTITY_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.config_types")

val CONFIG_OBJECT_INDEX_KEY: ID<String, DrupalConfigObject> =
    ID.create("com.github.nvelychenko.drupalextend.index.config_objects")

val CONTENT_ENTITY_INDEX_KEY: ID<String, DrupalContentEntity> =
    ID.create("com.github.nvelychenko.drupalextend.index.content_types")

val CONTENT_ENTITY_FQN_INDEX_KEY: ID<String, ContentEntity> =
    ID.create("com.github.nvelychenko.drupalextend.index.content_entity_fqn")

val FIELDS_INDEX_KEY: ID<String, DrupalField> =
    ID.create("com.github.nvelychenko.drupalextend.index.fields")

val FIELD_TYPE_INDEX_KEY: ID<String, DrupalFieldType> =
    ID.create("com.github.nvelychenko.drupalextend.index.field_type")

val FIELD_PLUGIN_DEFINITION_INDEX_KEY: ID<String, DrupalFieldPluginDefinition> =
    ID.create("com.github.nvelychenko.drupalextend.index.field_plugin_definition")

val PLUGIN_CONFIG_USAGE_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.plugin_config_usage")

val PLUGIN_DEFINITION_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.plugin_definition")

val HOOK_ATTRIBUTES_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.hook_attributes")

val LIBRARIES_INDEX_KEY: ID<String, Void?> =
    ID.create("com.github.nvelychenko.drupalextend.index.libraries")

val LINKS_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.links")

val PERMISSIONS_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.permissions")

val RENDER_ELEMENT_INDEX_KEY: ID<String, RenderElementType> =
    ID.create("com.github.nvelychenko.drupalextend.index.render_element")

val ROUTES_INDEX_KEY: ID<String, DrupalRoute> =
    ID.create("com.github.nvelychenko.drupalextend.index.routes")

val SERVICES_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.services")

val SERVICE_FACTORY_INDEX_KEY: ID<String, DrupalServiceFactory> =
    ID.create("com.github.nvelychenko.drupalextend.index.service_factory")

val SERVICE_TAGS_INDEX_KEY: ID<String, DrupalServiceTag> =
    ID.create("com.github.nvelychenko.drupalextend.index.service_tags")

val MODULE_INDEX_KEY: ID<String, String> =
    ID.create("com.github.nvelychenko.drupalextend.index.modules")

val THEME_INDEX_KEY: ID<String, DrupalTheme> =
    ID.create("com.github.nvelychenko.drupalextend.index.hook_themes")
