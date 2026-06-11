package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.MODULE_INDEX_KEY
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex

object Modules {

    fun isSupportEnabled(project: Project): Boolean {
        return project.drupalExtendSettings.isEnabled
    }

    /** All extension machine names known to the project (modules, themes, profiles). */
    fun allMachineNames(project: Project): List<String> {
        return FileBasedIndex.getInstance().getAllProjectKeys(MODULE_INDEX_KEY, project)
    }

    /** The extension type (`module`, `theme`, `profile`, …) or null when unknown. */
    fun extensionType(project: Project, machineName: String): String? {
        return FileBasedIndex.getInstance()
            .getValue(MODULE_INDEX_KEY, machineName, project)
            ?.takeIf { it.isNotBlank() }
    }

    fun exists(project: Project, machineName: String): Boolean {
        return FileBasedIndex.getInstance().getValue(MODULE_INDEX_KEY, machineName, project) != null
    }
}
