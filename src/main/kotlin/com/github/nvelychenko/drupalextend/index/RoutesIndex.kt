package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalRoute
import com.github.nvelychenko.drupalextend.index.types.DrupalRouteHandler
import com.github.nvelychenko.drupalextend.util.RoutingYaml
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

class RoutesIndex : FileBasedIndexExtension<String, DrupalRoute>() {
    override fun getIndexer(): DataIndexer<String, DrupalRoute, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, DrupalRoute>()

            if (!RoutingYaml.isRoutingFileName(inputData.file.name)) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer map
            RoutingYaml.routeDefinitions(yamlFile)
                .forEach { routeDefinition ->
                    val handlers = RoutingYaml.routeHandlers(routeDefinition)
                    val primaryHandler = handlers.firstOrNull()
                    val routeName = routeDefinition.keyText
                    map[routeName] = DrupalRoute(
                        name = routeName,
                        path = RoutingYaml.routePath(routeDefinition),
                        parameters = RoutingYaml.routePathParameters(routeDefinition),
                        handlerClass = primaryHandler?.className,
                        handlerMethod = primaryHandler?.methodName,
                        handlers = handlers.map { DrupalRouteHandler(it.className, it.methodName) }
                    )
                }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<DrupalRoute> = SerializedObjectDataExternalizer(DrupalRoute.serializer())

    override fun getName(): ID<String, DrupalRoute> {
        return ROUTES_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<DrupalRoute> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile ->
            file.fileType == YAMLFileType.YML && RoutingYaml.isRoutingFileName(file.name)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 6

}
