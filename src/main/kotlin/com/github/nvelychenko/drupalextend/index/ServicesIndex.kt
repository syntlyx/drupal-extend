package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
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
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class ServicesIndex : FileBasedIndexExtension<String, String>() {
    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()

            if (!ServicesYaml.isServicesFileName(inputData.file.name)) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            val servicesMapping = ServicesYaml.servicesMapping(PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java))
                ?: return@DataIndexer map

            servicesMapping.keyValues
                .filter { it.keyText.isNotBlank() && !it.keyText.startsWith("_") }
                .forEach { keyValue ->
                    val serviceId = keyValue.keyText
                    val explicitClass = ServicesYaml.serviceClass(keyValue)?.takeIf { it.isNotBlank() }
                    // Modern autowired style: the service id IS the FQCN and `class:` is omitted
                    // (`Drupal\my_module\MyService: ~`) — fall back to the id as the class.
                    map[serviceId] = explicitClass
                        ?: serviceId.takeIf { it.contains('\\') }.orEmpty()
                }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return SERVICES_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && ServicesYaml.isServicesFileName(file.name)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 3

}
