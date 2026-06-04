package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.DrupalFieldPlugins
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
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class FieldPluginConfigUsageIndex : FileBasedIndexExtension<String, String>() {
    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()

            if (!inputData.project.drupalExtendSettings.isEnabled) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            when {
                isFieldStorageConfig(yamlFile.name) -> indexFieldStorageConfig(map, yamlFile)
                isEntityFormDisplayConfig(yamlFile.name) -> indexDisplayConfig(map, yamlFile, DrupalFieldPlugins.FIELD_WIDGET)
                isEntityViewDisplayConfig(yamlFile.name) -> indexDisplayConfig(map, yamlFile, DrupalFieldPlugins.FIELD_FORMATTER)
            }

            map
        }
    }

    private fun indexFieldStorageConfig(map: HashMap<String, String>, yamlFile: YAMLFile) {
        val type = PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .firstOrNull { it.keyPath == "type" }
            ?.stringValue()
            ?: return

        map[DrupalFieldPlugins.configUsageKey(DrupalFieldPlugins.FIELD_TYPE, type)] = yamlFile.name
    }

    private fun indexDisplayConfig(map: HashMap<String, String>, yamlFile: YAMLFile, pluginKind: String) {
        PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
            .filter { it.isContentPluginTypeKey() }
            .mapNotNull { it.stringValue() }
            .forEach { pluginId ->
                map[DrupalFieldPlugins.configUsageKey(pluginKind, pluginId)] = yamlFile.name
            }
    }

    private fun YAMLKeyValue.isContentPluginTypeKey(): Boolean {
        val segments = keyPath.split(".")
        return segments.size == 3 && segments[0] == "content" && segments[2] == "type"
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

    private fun isEntityFormDisplayConfig(fileName: String): Boolean {
        return (fileName.startsWith("core.entity_form_display.") || fileName.startsWith("entity_form_display.")) &&
                fileName.endsWith(".yml")
    }

    private fun isEntityViewDisplayConfig(fileName: String): Boolean {
        return (fileName.startsWith("core.entity_view_display.") || fileName.startsWith("entity_view_display.")) &&
                fileName.endsWith(".yml")
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return FIELD_PLUGIN_CONFIG_USAGE_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && (
                isFieldStorageConfig(file.name) ||
                    isEntityFormDisplayConfig(file.name) ||
                    isEntityViewDisplayConfig(file.name)
                )
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1
}
