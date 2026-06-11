package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Permissions
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion

class DrupalPermissionsSchemaCatalogExclusion : JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        if (!Permissions.isPermissionsFileName(file.name)) return false

        return ProjectLocator.getInstance()
            .getProjectsForFile(file)
            .filterNotNull()
            .any { Permissions.isSupportEnabled(it) }
    }
}
