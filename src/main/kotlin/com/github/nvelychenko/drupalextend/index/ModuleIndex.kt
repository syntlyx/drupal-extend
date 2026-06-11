package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
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
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Indexes Drupal extensions from `*.info.yml` descriptors: machine name → extension type
 * (`module`, `theme`, `profile`, …). Powers module machine-name completion/navigation in test
 * `$modules` arrays, `moduleExists()` calls, and info.yml `dependencies`.
 */
class ModuleIndex : FileBasedIndexExtension<String, String>() {

    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()

            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            val machineName = inputData.file.name.removeSuffix(".info.yml")
            if (machineName.isBlank()) return@DataIndexer map

            val type = PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java)
                ?.keyValues
                ?.firstOrNull { it.keyText == "type" }
                ?.valueText
                .orEmpty()

            map[machineName] = type

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return MODULE_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && file.name.endsWith(".info.yml")
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

}
