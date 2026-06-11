package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion

class DrupalRoutingSchemaCatalogExclusion : JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        if (!RoutingYaml.isRoutingFileName(file.name)) return false

        return ProjectLocator.getInstance()
            .getProjectsForFile(file)
            .filterNotNull()
            .any { Routes.isSupportEnabled(it) }
    }
}
