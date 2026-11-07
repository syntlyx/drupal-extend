package com.github.nvelychenko.drupalextend.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile

/**
 * Contributes Drupal config-sync directories (see [DrupalConfigDirectories]) as a synthetic
 * library so their YAML is indexed even when the directory is excluded from the project (a
 * common setup: `config/` excluded to reduce indexing, BLT's `config/default`,
 * `drupal/recommended-project`'s `config/sync`). Excluded folders are otherwise invisible to
 * every file-based index, which silently disables all config-driven features — plugin usage
 * detection, config name completion, bundle ids. This is the same mechanism the platform uses
 * to index excluded `node_modules`.
 */
class DrupalConfigLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val configDirectories = DrupalConfigDirectories.findAll(project)
        if (configDirectories.isEmpty()) return emptyList()

        return listOf(SyntheticLibrary.newImmutableLibrary(configDirectories))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        return DrupalConfigDirectories.findAll(project)
    }
}
