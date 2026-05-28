package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion

class DrupalServicesSchemaCatalogExclusion : JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        if (!ServicesYaml.isServicesFileName(file.name)) return false

        return ProjectLocator.getInstance()
            .getProjectsForFile(file)
            .filterNotNull()
            .any { Services.isSupportEnabled(it) }
    }
}
