package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalConfigObject
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
import kotlinx.serialization.serializer
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Indexes configuration objects: module defaults under `config/install`/`config/optional` and
 * site sync exports anywhere (config files are recognized by their dotted machine name, e.g.
 * `system.site.yml`, `views.view.frontpage.yml`). The value carries the flattened key paths of
 * the file's content for `Config::get('a.b')` completion.
 */
class ConfigObjectIndex : FileBasedIndexExtension<String, DrupalConfigObject>() {

    override fun getIndexer(): DataIndexer<String, DrupalConfigObject, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, DrupalConfigObject>()

            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            val configName = inputData.file.name.removeSuffix(".yml")

            val rootMapping = PsiTreeUtil.getChildOfType(yamlFile, YAMLDocument::class.java)
                ?.topLevelValue as? YAMLMapping
                ?: return@DataIndexer map

            val keyPaths = mutableListOf<String>()
            collectKeyPaths(rootMapping, prefix = "", depth = 0, into = keyPaths)

            map[configName] = DrupalConfigObject(configName, keyPaths)

            map
        }
    }

    private fun collectKeyPaths(mapping: YAMLMapping, prefix: String, depth: Int, into: MutableList<String>) {
        if (depth >= MAX_KEY_DEPTH || into.size >= MAX_KEY_PATHS) return

        for (keyValue in mapping.keyValues) {
            val key = keyValue.keyText.takeIf { it.isNotBlank() } ?: continue
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            into.add(path)
            if (into.size >= MAX_KEY_PATHS) return

            val value = keyValue.value
            if (value is YAMLMapping) {
                collectKeyPaths(value, path, depth + 1, into)
            }
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<DrupalConfigObject> =
        SerializedObjectDataExternalizer(serializer<DrupalConfigObject>())

    override fun getName(): ID<String, DrupalConfigObject> {
        return CONFIG_OBJECT_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<DrupalConfigObject> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && isConfigObjectFileName(file.name)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

    companion object {
        private const val MAX_KEY_DEPTH = 5
        private const val MAX_KEY_PATHS = 500

        // Config object machine names: lowercase words joined by dots, at least two segments
        // (`system.site`, `views.view.frontpage`).
        private val CONFIG_FILE_NAME = Regex("""[a-z0-9_]+(\.[a-z0-9_]+)+\.yml""")

        // Dotted YAML files that are NOT config objects and have their own subsystems.
        private val NON_CONFIG_SUFFIXES = listOf(
            ".services.yml",
            ".routing.yml",
            ".permissions.yml",
            ".libraries.yml",
            ".info.yml",
            ".schema.yml",
            ".breakpoints.yml",
            ".links.menu.yml",
            ".links.task.yml",
            ".links.action.yml",
            ".links.contextual.yml",
            ".post_update.yml",
            ".api.yml",
        )

        fun isConfigObjectFileName(fileName: String): Boolean {
            if (!CONFIG_FILE_NAME.matches(fileName)) return false

            return NON_CONFIG_SUFFIXES.none { fileName.endsWith(it) }
        }
    }
}
