package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isInConfigurationDirectory
import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.yml.YAMLKeyValueFinder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

class PermissionsIndex : FileBasedIndexExtension<String, String>() {
    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()
            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            if (!inputData.project.drupalExtendSettings.isEnabled) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            if (inputData.file.name.endsWith(".permissions.yml")) {
                PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java)
                    ?.keyValues
                    ?.forEach {
                        if (it.keyText != "permission_callbacks") {
                            map[it.keyText] = it.keyPath
                        }
                    }
            }

            if (
                inputData.file.name.startsWith("user.role.") &&
                inputData.file.name.endsWith(".yml") &&
                inputData.isInConfigurationDirectory()
            ) {
                val permissionsSequence = YAMLKeyValueFinder("permissions")
                    .findIn(yamlFile)
                    ?.value as? YAMLSequence ?: return@DataIndexer map
                for (permissionSequenceItem in permissionsSequence.items) {
                    // Malformed/in-progress YAML can put non-scalar items here.
                    val permission = permissionSequenceItem.value as? YAMLScalar ?: continue
                    map[permission.textValue] = "permissions"
                }
            }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return PERMISSIONS_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    private val myDataExternalizer: DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        // The indexer only reads `*.permissions.yml` and `user.role.*.yml` — don't parse
        // every other YAML file in the project during indexing.
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML
                    && (file.name.endsWith(".permissions.yml") || file.name.startsWith("user.role."))
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

}
