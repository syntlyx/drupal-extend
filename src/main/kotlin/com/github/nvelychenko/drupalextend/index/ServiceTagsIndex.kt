package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalServiceTag
import com.github.nvelychenko.drupalextend.util.ServicesYaml
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
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

/**
 * Indexes service tags from `*.services.yml`: tag name → the service ids carrying it (per
 * file). Powers tag-name completion under `tags:`, `!tagged_iterator` validation, and
 * navigation from a tag to its tagged services. Kept separate from [ServicesIndex] on purpose —
 * its plain `id → class` value is consumed all over the plugin and must not change shape.
 */
class ServiceTagsIndex : FileBasedIndexExtension<String, DrupalServiceTag>() {

    override fun getIndexer(): DataIndexer<String, DrupalServiceTag, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, DrupalServiceTag>()

            if (!ServicesYaml.isServicesFileName(inputData.file.name)) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            val servicesMapping = ServicesYaml.servicesMapping(
                PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java)
            ) ?: return@DataIndexer map

            val serviceIdsByTag = hashMapOf<String, MutableList<String>>()
            servicesMapping.keyValues
                .filter { ServicesYaml.isServiceDefinition(it) }
                .forEach { serviceKeyValue ->
                    val definition = serviceKeyValue.value as? YAMLMapping ?: return@forEach
                    val tagsSequence = definition.keyValues
                        .firstOrNull { it.keyText == "tags" }
                        ?.value as? YAMLSequence
                        ?: return@forEach

                    for (item in tagsSequence.items) {
                        val tagName = when (val itemValue = item.value) {
                            is YAMLMapping -> itemValue.keyValues.firstOrNull { it.keyText == "name" }?.valueText
                            else -> itemValue?.text?.trim('\'', '"')
                        }?.takeIf { it.isNotBlank() } ?: continue

                        serviceIdsByTag.getOrPut(tagName) { mutableListOf() }.add(serviceKeyValue.keyText)
                    }
                }

            serviceIdsByTag.forEach { (tagName, serviceIds) ->
                map[tagName] = DrupalServiceTag(tagName, serviceIds)
            }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<DrupalServiceTag> =
        SerializedObjectDataExternalizer(serializer<DrupalServiceTag>())

    override fun getName(): ID<String, DrupalServiceTag> {
        return SERVICE_TAGS_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<DrupalServiceTag> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && ServicesYaml.isServicesFileName(file.name)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

}
