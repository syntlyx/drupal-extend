package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex

object Links {
    fun isSupportEnabled(project: Project): Boolean {
        return Routes.isSupportEnabled(project)
    }

    fun allIds(project: Project): Collection<String> {
        if (!isSupportEnabled(project)) return emptyList()
        return FileBasedIndex.getInstance()
            .getAllProjectKeys(LINKS_INDEX_KEY, project)
            .sorted()
    }

    fun title(project: Project, linkId: String): String? {
        if (!isSupportEnabled(project)) return null
        return FileBasedIndex.getInstance().getValue(LINKS_INDEX_KEY, linkId, project)
    }

    fun exists(project: Project, linkId: String): Boolean {
        return title(project, linkId) != null
    }
}
