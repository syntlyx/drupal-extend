package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalFieldPluginDefinition
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
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass

class FieldPluginDefinitionIndex : FileBasedIndexExtension<String, DrupalFieldPluginDefinition>() {
    override fun getIndexer(): DataIndexer<String, DrupalFieldPluginDefinition, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, DrupalFieldPluginDefinition>()

            if (!inputData.project.drupalExtendSettings.isEnabled) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val phpFile = inputData.psiFile as? PhpFile ?: return@DataIndexer map
            PsiTreeUtil.findChildrenOfType(phpFile, PhpClass::class.java)
                .mapNotNull { DrupalFieldPlugins.definition(it) }
                .forEach { definition ->
                    map[DrupalFieldPlugins.definitionKey(definition.kind, definition.id)] = definition
                }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<DrupalFieldPluginDefinition> =
        SerializedObjectDataExternalizer(DrupalFieldPluginDefinition.serializer())

    override fun getName(): ID<String, DrupalFieldPluginDefinition> {
        return FIELD_PLUGIN_DEFINITION_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<DrupalFieldPluginDefinition> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == PhpFileType.INSTANCE
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 4
}
