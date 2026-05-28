package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion

class DrupalServicesSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(DrupalServicesSchemaProvider(project))
    }

    private class DrupalServicesSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean {
            return Services.isSupportEnabled(project) && ServicesYaml.isServicesFileName(file.name)
        }

        override fun getName(): String = "Drupal services.yml"

        override fun getPresentableName(): String = name

        override fun getSchemaFile(): VirtualFile? {
            return JsonSchemaProviderFactory.getResourceFile(
                DrupalServicesSchemaProviderFactory::class.java,
                "/schemas/drupal-services.schema.json"
            )
        }

        override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

        override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

        override fun isUserVisible(): Boolean = false
    }
}
