package com.github.nvelychenko.drupalextend.project

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Locates the project's Drupal config-sync directories — recognized by their
 * `core.extension.yml`, present in every sync export and nowhere else. The scan is
 * depth-capped and prunes trees that can't contain a sync directory; the result is cached
 * against VFS structure changes.
 *
 * Consumers: [DrupalConfigLibraryRootsProvider] (keeps the directories indexed even when
 * excluded) and the plugin-id references search (keeps them searchable even when the chosen
 * scope — e.g. the default "Project Files" — doesn't cover excluded/library locations).
 */
object DrupalConfigDirectories {
    fun findAll(project: Project): List<VirtualFile> {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                scanForConfigDirectories(project),
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
            )
        }
    }

    /** Scope over the config directories, or null when the project has none. */
    fun searchScope(project: Project): GlobalSearchScope? {
        val directories = findAll(project)
        if (directories.isEmpty()) return null

        return GlobalSearchScopesCore.directoriesScope(project, true, *directories.toTypedArray())
    }

    private fun scanForConfigDirectories(project: Project): List<VirtualFile> {
        val baseDir = project.guessProjectDir() ?: return emptyList()
        val result = mutableListOf<VirtualFile>()
        collectConfigDirectories(baseDir, 0, result)

        return result
    }

    private fun collectConfigDirectories(directory: VirtualFile, depth: Int, result: MutableList<VirtualFile>) {
        if (result.size >= MAX_CONFIG_DIRECTORIES) return
        ProgressManager.checkCanceled()

        if (directory.findChild(CONFIG_DIRECTORY_MARKER) != null) {
            result.add(directory)
            return
        }
        if (depth >= MAX_SCAN_DEPTH) return

        for (child in directory.children) {
            if (child.isDirectory && !child.name.startsWith(".") && child.name !in SKIP_DIRECTORY_NAMES) {
                collectConfigDirectories(child, depth + 1, result)
            }
        }
    }
}

private const val CONFIG_DIRECTORY_MARKER = "core.extension.yml"

private const val MAX_SCAN_DEPTH = 3

private const val MAX_CONFIG_DIRECTORIES = 8

/** Trees a sync directory never lives under; pruned purely for scan cost. */
private val SKIP_DIRECTORY_NAMES = setOf(
    "vendor",
    "node_modules",
    "core",
    "modules",
    "themes",
    "profiles",
    "libraries",
    "files",
)
