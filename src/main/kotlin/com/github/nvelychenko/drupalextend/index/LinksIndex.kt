package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.util.RoutingYaml
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

class LinksIndex : FileBasedIndexExtension<String, String>() {
    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()

            if (!RoutingYaml.isLinksFileName(inputData.file.name)) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            val rootMapping = PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java)
                ?: return@DataIndexer map

            rootMapping.keyValues
                .filter { RoutingYaml.isLinkDefinition(it) }
                .forEach { map[it.keyText] = RoutingYaml.linkTitle(it) }

            map
        }
    }

    override fun getName(): ID<String, String> {
        return LINKS_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && RoutingYaml.isLinksFileName(file.name)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 2

}
