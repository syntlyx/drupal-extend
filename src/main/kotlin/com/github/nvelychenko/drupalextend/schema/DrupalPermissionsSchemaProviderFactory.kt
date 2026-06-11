package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Permissions
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion

class DrupalPermissionsSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(DrupalPermissionsSchemaProvider(project))
    }

    private class DrupalPermissionsSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean {
            return Permissions.isSupportEnabled(project) && Permissions.isPermissionsFileName(file.name)
        }

        override fun getName(): String = "Drupal permissions.yml"

        override fun getPresentableName(): String = name

        override fun getSchemaFile(): VirtualFile? {
            return JsonSchemaProviderFactory.getResourceFile(
                DrupalPermissionsSchemaProviderFactory::class.java,
                "/schemas/drupal-permissions.schema.json"
            )
        }

        override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

        override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

        override fun isUserVisible(): Boolean = false
    }
}
