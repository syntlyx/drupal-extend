package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion

class DrupalRoutingSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(DrupalRoutingSchemaProvider(project))
    }

    private class DrupalRoutingSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean {
            return Routes.isSupportEnabled(project) && RoutingYaml.isRoutingFileName(file.name)
        }

        override fun getName(): String = "Drupal routing.yml"

        override fun getPresentableName(): String = name

        override fun getSchemaFile(): VirtualFile? {
            return JsonSchemaProviderFactory.getResourceFile(
                DrupalRoutingSchemaProviderFactory::class.java,
                "/schemas/drupal-routing.schema.json"
            )
        }

        override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

        override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

        override fun isUserVisible(): Boolean = false
    }
}
