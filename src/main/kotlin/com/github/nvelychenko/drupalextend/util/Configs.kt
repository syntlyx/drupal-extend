package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.CONFIG_OBJECT_INDEX_KEY
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

object Configs {

    fun isSupportEnabled(project: Project): Boolean {
        return project.drupalExtendSettings.isEnabled
    }

    fun allNames(project: Project): List<String> {
        return FileBasedIndex.getInstance().getAllProjectKeys(CONFIG_OBJECT_INDEX_KEY, project)
    }

    fun keyPaths(project: Project, configName: String): List<String> {
        return FileBasedIndex.getInstance()
            .getValue(CONFIG_OBJECT_INDEX_KEY, configName, project)
            ?.keyPaths
            .orEmpty()
    }

    fun exists(project: Project, configName: String): Boolean {
        return FileBasedIndex.getInstance().getValue(CONFIG_OBJECT_INDEX_KEY, configName, project) != null
    }

    /**
     * True for the calls whose first string argument is a config object name:
     * `\Drupal::config('…')`, `$this->config('…')` (ControllerBase/ConfigFormBase),
     * and `…configFactory…->get('…')` / `->getEditable('…')`.
     */
    fun isConfigNameCall(methodReference: MethodReference): Boolean {
        return when (methodReference.name) {
            "config" -> true
            "get", "getEditable" -> {
                val receiverText = methodReference.classReference?.text?.lowercase() ?: return false
                "configfactory" in receiverText || "config_factory" in receiverText || "config.factory" in receiverText
            }

            else -> false
        }
    }

    /** Whether the file is a `settings.php`-family file (`settings.php`, `settings.local.php`, …). */
    fun isSettingsPhpFileName(fileName: String): Boolean {
        return fileName.startsWith("settings.") && fileName.endsWith(".php")
    }

    /** settings.php: `$config['system.site']` — the literal is the config NAME index. */
    fun isSettingsConfigOverrideName(literal: StringLiteralExpression): Boolean {
        if (!isSettingsPhpFileName(literal.containingFile?.name.orEmpty())) return false
        val access = literal.enclosingArrayAccess() ?: return false

        return (access.value as? Variable)?.name == "config"
    }

    /**
     * settings.php: `$config['system.site']['page…']` — when the literal is the KEY (second)
     * index, returns the config object name from the first index; null otherwise.
     */
    fun settingsConfigOverrideNameForKey(literal: StringLiteralExpression): String? {
        if (!isSettingsPhpFileName(literal.containingFile?.name.orEmpty())) return null
        val access = literal.enclosingArrayAccess() ?: return null
        val nameAccess = access.value as? ArrayAccessExpression ?: return null
        if ((nameAccess.value as? Variable)?.name != "config") return null

        return (nameAccess.index?.value as? StringLiteralExpression)
            ?.contents
            ?.takeIf { it.isNotBlank() }
    }

    /** settings.php: `$settings['…']` — the literal is the settings key index. */
    fun isSettingsArrayKey(literal: StringLiteralExpression): Boolean {
        if (!isSettingsPhpFileName(literal.containingFile?.name.orEmpty())) return false
        val access = literal.enclosingArrayAccess() ?: return false

        return (access.value as? Variable)?.name == "settings"
    }

    private fun StringLiteralExpression.enclosingArrayAccess(): ArrayAccessExpression? {
        val index = parent as? ArrayIndex ?: return null

        return index.parent as? ArrayAccessExpression
    }
}
