package com.github.nvelychenko.drupalextend.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class DrupalCoreVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<DrupalCoreVersion> {
    override fun compareTo(other: DrupalCoreVersion): Int {
        return compareValuesBy(this, other, DrupalCoreVersion::major, DrupalCoreVersion::minor, DrupalCoreVersion::patch)
    }
}

internal object DrupalProjectVersion {
    private val json = Json { ignoreUnknownKeys = true }
    private val corePackageNames = setOf("drupal/core-recommended", "drupal/core")
    private val versionRegex = Regex("""v?(\d+)(?:\.(\d+|x|\*))?(?:\.(\d+|x|\*))?""")
    private val fieldWidgetAttributeVersion = DrupalCoreVersion(10, 3, 0)

    fun supportsFieldWidgetAttributes(project: Project): Boolean {
        return isAtLeast(project, fieldWidgetAttributeVersion)
    }

    fun isAtLeast(project: Project, version: DrupalCoreVersion): Boolean {
        return detect(project)?.let { it >= version } == true
    }

    fun detect(project: Project): DrupalCoreVersion? {
        project.drupalExtendSettings.drupalCoreVersion
            .takeIf { it.isNotBlank() }
            ?.toDrupalCoreVersion()
            ?.let { return it }

        val projectDir = project.guessProjectDir() ?: return null
        val composerLock = projectDir.findChild("composer.lock")
        if (composerLock != null) {
            parseComposerLock(VfsUtilCore.loadText(composerLock))?.let { return it }
        }

        val composerJson = projectDir.findChild("composer.json") ?: return null
        return parseComposerJson(VfsUtilCore.loadText(composerJson))
    }

    private fun parseComposerLock(text: String): DrupalCoreVersion? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val packages = buildList {
            addAll(root["packages"].asArrayOrEmpty())
            addAll(root["packages-dev"].asArrayOrEmpty())
        }

        return packages
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .firstNotNullOfOrNull { dependency ->
                val name = dependency["name"].asString()
                if (name !in corePackageNames) return@firstNotNullOfOrNull null

                dependency["version"].asString()?.toDrupalCoreVersion()
            }
    }

    private fun parseComposerJson(text: String): DrupalCoreVersion? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val requirements = buildList {
            add(root["require"] as? JsonObject)
            add(root["require-dev"] as? JsonObject)
        }.filterNotNull()

        return corePackageNames.firstNotNullOfOrNull { packageName ->
            requirements.firstNotNullOfOrNull { it[packageName].asString()?.toDrupalCoreVersion() }
        }
    }

    private fun String.toDrupalCoreVersion(): DrupalCoreVersion? {
        val match = versionRegex.find(this) ?: return null
        return DrupalCoreVersion(
            major = match.groupValues[1].toIntOrNull() ?: return null,
            minor = match.groupValues[2].toVersionPart(),
            patch = match.groupValues[3].toVersionPart(),
        )
    }

    private fun String.toVersionPart(): Int {
        return takeIf { it.isNotBlank() && it != "x" && it != "*" }?.toIntOrNull() ?: 0
    }

    private fun Any?.asArrayOrEmpty(): JsonArray {
        return (this as? JsonArray) ?: JsonArray(emptyList())
    }

    private fun Any?.asString(): String? {
        return (this as? JsonPrimitive)?.jsonPrimitive?.content
    }
}
