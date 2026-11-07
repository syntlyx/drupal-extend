package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.util.DrupalConfigUsagePlugins
import com.github.nvelychenko.drupalextend.util.DrupalFieldPlugins
import com.github.nvelychenko.drupalextend.util.DrupalPluginConfigYaml
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Where plugin ids are *used*, keyed `kind|id` (see [usageKey]).
 *
 * Config side: `field.storage.*` / `field.field.*` reference field types,
 * `core.entity_form_display.*` references widgets, `core.entity_view_display.*` references
 * formatters (`content.<field>.type`) plus Layout Builder formatters, block components, and
 * layouts; `views.view.*` references formatters and every views handler/display plugin; and
 * the single-plugin config entities enable their plugin kinds (`rest.resource.*`,
 * `block.block.*`, `system.action.*`, `media.type.*`, `image.style.*`, `filter.format.*` —
 * see [DrupalConfigUsagePlugins]). Code side: base field definitions —
 * `BaseFieldDefinition::create('<field_type>')` and
 * `->setDisplayOptions('form'|'view', ['type' => '<plugin_id>'])`.
 */
class PluginConfigUsageIndex : FileBasedIndexExtension<String, String>() {
    companion object {
        fun usageKey(kind: String, id: String): String {
            return "$kind|$id"
        }
    }

    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()

            if (!inputData.isValidForIndex()) return@DataIndexer map

            when (val psiFile = inputData.psiFile) {
                is YAMLFile -> when {
                    isFieldStorageConfig(psiFile.name) -> indexFieldStorageConfig(map, psiFile)
                    isFieldInstanceConfig(psiFile.name) -> indexFieldInstanceConfig(map, psiFile)
                    isEntityFormDisplayConfig(psiFile.name) -> indexDisplayConfig(map, psiFile, DrupalFieldPlugins.FIELD_WIDGET)
                    isEntityViewDisplayConfig(psiFile.name) -> {
                        indexDisplayConfig(map, psiFile, DrupalFieldPlugins.FIELD_FORMATTER)
                        indexLayoutBuilderComponents(map, psiFile)
                    }
                    isViewsConfig(psiFile.name) -> indexViewsConfig(map, psiFile)
                    isRestResourceConfig(psiFile.name) ->
                        indexTopLevelPluginKey(map, psiFile, "plugin_id", DrupalConfigUsagePlugins.REST_RESOURCE)
                    isBlockPlacementConfig(psiFile.name) ->
                        indexTopLevelPluginKey(map, psiFile, "plugin", DrupalConfigUsagePlugins.BLOCK)
                    isActionConfig(psiFile.name) ->
                        indexTopLevelPluginKey(map, psiFile, "plugin", DrupalConfigUsagePlugins.ACTION)
                    isMediaTypeConfig(psiFile.name) ->
                        indexTopLevelPluginKey(map, psiFile, "source", DrupalConfigUsagePlugins.MEDIA_SOURCE)
                    isImageStyleConfig(psiFile.name) ->
                        indexNestedIdConfig(map, psiFile, "effects", DrupalConfigUsagePlugins.IMAGE_EFFECT)
                    isFilterFormatConfig(psiFile.name) ->
                        indexNestedIdConfig(map, psiFile, "filters", DrupalConfigUsagePlugins.FILTER)
                }
                is PhpFile -> indexPhpBaseFieldDefinitions(map, psiFile, inputData)
            }

            map
        }
    }

    private fun indexFieldStorageConfig(map: HashMap<String, String>, yamlFile: YAMLFile) {
        val type = PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .firstOrNull { it.keyPath == "type" }
            ?.stringValue()
            ?: return

        map[usageKey(DrupalFieldPlugins.FIELD_TYPE, type)] = yamlFile.name
    }

    private fun indexFieldInstanceConfig(map: HashMap<String, String>, yamlFile: YAMLFile) {
        val type = PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .firstOrNull { it.keyPath == "field_type" }
            ?.stringValue()
            ?: return

        map[usageKey(DrupalFieldPlugins.FIELD_TYPE, type)] = yamlFile.name
    }

    private fun indexDisplayConfig(map: HashMap<String, String>, yamlFile: YAMLFile, pluginKind: String) {
        PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .filter { it.isContentPluginTypeKey() }
            .mapNotNull { it.stringValue() }
            .forEach { pluginId ->
                map[usageKey(pluginKind, pluginId)] = yamlFile.name
            }
    }

    /**
     * Layout Builder components (see [DrupalPluginConfigYaml.layoutBuilderPluginKindAt] for the
     * block/layout positions): field blocks additionally carry a formatter id at
     * `…configuration.formatter.type`, which belongs to the field-plugin domain.
     */
    private fun indexLayoutBuilderComponents(map: HashMap<String, String>, yamlFile: YAMLFile) {
        for (keyValue in PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)) {
            val keyPath = keyValue.keyPath
            if (!keyPath.contains("layout_builder.")) continue
            val value = keyValue.stringValue() ?: continue

            if (keyPath.endsWith(".formatter.type")) {
                map[usageKey(DrupalFieldPlugins.FIELD_FORMATTER, value)] = yamlFile.name
            } else {
                DrupalPluginConfigYaml.layoutBuilderPluginKindAt(keyPath)?.let { kind ->
                    registerPluginId(map, kind, value, yamlFile.name)
                }
            }
        }
    }

    /**
     * `views.view.*` plugin positions (see [DrupalPluginConfigYaml.viewsPluginKindAt]); field
     * handlers additionally store the field *formatter* id at
     * `display_options.fields.<field>.type`, which belongs to the field-plugin domain.
     */
    private fun indexViewsConfig(map: HashMap<String, String>, yamlFile: YAMLFile) {
        for (keyValue in PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)) {
            val segments = keyValue.keyPath.split(".")
            val value = keyValue.stringValue() ?: continue

            if (isViewsFieldFormatterTypeKey(segments)) {
                map[usageKey(DrupalFieldPlugins.FIELD_FORMATTER, value)] = yamlFile.name
            } else {
                DrupalPluginConfigYaml.viewsPluginKindAt(segments)?.let { kind ->
                    registerPluginId(map, kind, value, yamlFile.name)
                }
            }
        }
    }

    /**
     * Single-plugin config entities keep the enabled plugin id under one well-known top-level
     * key: `rest.resource.*` → `plugin_id`, `block.block.*` / `system.action.*` → `plugin`,
     * `media.type.*` → `source`.
     */
    private fun indexTopLevelPluginKey(map: HashMap<String, String>, yamlFile: YAMLFile, key: String, kind: String) {
        val pluginId = PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .firstOrNull { it.keyPath == key }
            ?.stringValue()
            ?: return

        registerPluginId(map, kind, pluginId, yamlFile.name)
    }

    /**
     * Collection config entities list their plugins as `<rootKey>.<name>.id` mappings:
     * `image.style.*` → `effects.<uuid>.id`, `filter.format.*` → `filters.<name>.id`.
     */
    private fun indexNestedIdConfig(map: HashMap<String, String>, yamlFile: YAMLFile, rootKey: String, kind: String) {
        PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .filter {
                val segments = it.keyPath.split(".")
                segments.size == 3 && segments[0] == rootKey && segments.last() == "id"
            }
            .mapNotNull { it.stringValue() }
            .forEach { registerPluginId(map, kind, it, yamlFile.name) }
    }

    /**
     * Derived plugin ids (`system_menu_block:main`, `entity:publish_action:node`) are enabled
     * by config under the derivative id while the class declares only the base id — index
     * both so the deriver-owning class still matches.
     */
    private fun registerPluginId(map: HashMap<String, String>, kind: String, pluginId: String, fileName: String) {
        map[usageKey(kind, pluginId)] = fileName

        val baseId = pluginId.substringBefore(':')
        if (baseId != pluginId && baseId.isNotBlank()) {
            map[usageKey(kind, baseId)] = fileName
        }
    }

    /**
     * Base fields wire field plugins in code, not config:
     * `BaseFieldDefinition::create('<field_type>')` and
     * `->setDisplayOptions('form'|'view', ['type' => '<widget/formatter id>'])`.
     * Name-and-shape based on purpose — indexers must not resolve references.
     */
    private fun indexPhpBaseFieldDefinitions(map: HashMap<String, String>, phpFile: PhpFile, inputData: FileContent) {
        val text = inputData.contentAsText
        val hasCreate = FIELD_DEFINITION_CLASSES.any { text.contains(it) }
        val hasDisplayOptions = text.contains(SET_DISPLAY_OPTIONS)
        if (!hasCreate && !hasDisplayOptions) return

        for (reference in PsiTreeUtil.findChildrenOfType(phpFile, MethodReference::class.java)) {
            when (reference.name) {
                "create" -> {
                    if (!hasCreate) continue
                    val classReference = reference.classReference as? ClassReference ?: continue
                    if (classReference.name !in FIELD_DEFINITION_CLASSES) continue
                    val fieldType = reference.firstStringParameter() ?: continue
                    map[usageKey(DrupalFieldPlugins.FIELD_TYPE, fieldType)] = phpFile.name
                }
                SET_DISPLAY_OPTIONS -> {
                    val parameters = reference.parameters
                    val kind = when ((parameters.getOrNull(0) as? StringLiteralExpression)?.contents) {
                        "form" -> DrupalFieldPlugins.FIELD_WIDGET
                        "view" -> DrupalFieldPlugins.FIELD_FORMATTER
                        else -> continue
                    }
                    val options = parameters.getOrNull(1) as? ArrayCreationExpression ?: continue
                    val pluginId = options.hashElements
                        .firstOrNull { (it.key as? StringLiteralExpression)?.contents == "type" }
                        ?.value
                        ?.let { it as? StringLiteralExpression }
                        ?.contents
                        ?.takeIf { it.isNotBlank() }
                        ?: continue
                    map[usageKey(kind, pluginId)] = phpFile.name
                }
            }
        }
    }

    private fun MethodReference.firstStringParameter(): String? {
        return (parameters.firstOrNull() as? StringLiteralExpression)
            ?.contents
            ?.takeIf { it.isNotBlank() }
    }

    private fun YAMLKeyValue.isContentPluginTypeKey(): Boolean {
        val segments = keyPath.split(".")
        return segments.size == 3 && segments[0] == "content" && segments[2] == "type"
    }

    private fun isViewsFieldFormatterTypeKey(segments: List<String>): Boolean {
        return segments.size >= 5 &&
                segments.last() == "type" &&
                segments[segments.size - 3] == "fields" &&
                segments[segments.size - 4] == "display_options"
    }

    private fun YAMLKeyValue.stringValue(): String? {
        return (value as? YAMLScalar)
            ?.textValue
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun isFieldStorageConfig(fileName: String): Boolean {
        return fileName.startsWith("field.storage.") && fileName.endsWith(".yml")
    }

    private fun isFieldInstanceConfig(fileName: String): Boolean {
        return fileName.startsWith("field.field.") && fileName.endsWith(".yml")
    }

    private fun isEntityFormDisplayConfig(fileName: String): Boolean {
        return (fileName.startsWith("core.entity_form_display.") || fileName.startsWith("entity_form_display.")) &&
                fileName.endsWith(".yml")
    }

    private fun isEntityViewDisplayConfig(fileName: String): Boolean {
        return (fileName.startsWith("core.entity_view_display.") || fileName.startsWith("entity_view_display.")) &&
                fileName.endsWith(".yml")
    }

    private fun isViewsConfig(fileName: String): Boolean {
        return fileName.startsWith("views.view.") && fileName.endsWith(".yml")
    }

    private fun isRestResourceConfig(fileName: String) = DrupalPluginConfigYaml.isRestResourceConfig(fileName)

    private fun isBlockPlacementConfig(fileName: String) = DrupalPluginConfigYaml.isBlockPlacementConfig(fileName)

    private fun isActionConfig(fileName: String) = DrupalPluginConfigYaml.isActionConfig(fileName)

    private fun isMediaTypeConfig(fileName: String) = DrupalPluginConfigYaml.isMediaTypeConfig(fileName)

    private fun isImageStyleConfig(fileName: String) = DrupalPluginConfigYaml.isImageStyleConfig(fileName)

    private fun isFilterFormatConfig(fileName: String) = DrupalPluginConfigYaml.isFilterFormatConfig(fileName)

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return PLUGIN_CONFIG_USAGE_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == PhpFileType.INSTANCE || (
                file.fileType == YAMLFileType.YML && (
                    isFieldStorageConfig(file.name) ||
                        isFieldInstanceConfig(file.name) ||
                        isEntityFormDisplayConfig(file.name) ||
                        isEntityViewDisplayConfig(file.name) ||
                        isViewsConfig(file.name) ||
                        isRestResourceConfig(file.name) ||
                        isBlockPlacementConfig(file.name) ||
                        isActionConfig(file.name) ||
                        isMediaTypeConfig(file.name) ||
                        isImageStyleConfig(file.name) ||
                        isFilterFormatConfig(file.name)
                    )
                )
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 6
}

private const val SET_DISPLAY_OPTIONS = "setDisplayOptions"

private val FIELD_DEFINITION_CLASSES = setOf(
    "BaseFieldDefinition",
    "BundleFieldDefinition",
    "FieldDefinition",
)
