package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalServiceFactory
import com.github.nvelychenko.drupalextend.index.types.DrupalServiceFactoryOwner
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.vfs.VirtualFile
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

class ServiceFactoryIndex : FileBasedIndexExtension<String, DrupalServiceFactory>() {
    override fun getIndexer(): DataIndexer<String, DrupalServiceFactory, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, DrupalServiceFactory>()

            if (!Services.isSupportEnabled(inputData.project)) return@DataIndexer map
            if (!ServicesYaml.isServicesFileName(inputData.file.name)) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map

            // The index key is the method name. Within a single file two services can declare a
            // factory with the same method name but different owners; a Map<String, _> would drop
            // one, so all owners for a given method name are merged into the value's owner list.
            val ownersByMethod = linkedMapOf<String, MutableList<DrupalServiceFactoryOwner>>()
            ServicesYaml.serviceFactoryMethods(yamlFile)
                .forEach { factoryMethod ->
                    ownersByMethod
                        .getOrPut(factoryMethod.methodName) { mutableListOf() }
                        .add(DrupalServiceFactoryOwner(factoryMethod.ownerClassFqn, factoryMethod.ownerServiceId))
                }

            ownersByMethod.forEach { (methodName, owners) ->
                map[methodName] = DrupalServiceFactory(methodName, owners.toList())
            }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor()

    private val myDataExternalizer: DataExternalizer<DrupalServiceFactory> =
        SerializedObjectDataExternalizer(DrupalServiceFactory.serializer())

    override fun getName(): ID<String, DrupalServiceFactory> {
        return SERVICE_FACTORY_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<DrupalServiceFactory> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && ServicesYaml.isServicesFileName(file.name)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

}
