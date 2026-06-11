package com.github.nvelychenko.drupalextend.type

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

/**
 * Carries the config object name through the type system so that
 * `\Drupal::config('system.site')->get('…')` can complete key paths.
 *
 * `getType` is on the per-keystroke type-resolution path: it does cheap, dumb-guarded PSI
 * checks only and never touches the index. It emits an extra unresolvable `#`-marker type
 * (`#<KEY><SPLIT_KEY><configName>`) alongside Config's native type; `complete` returns null
 * so the marker is never resolved into a real class — it only exists for the completion
 * provider to read off the receiver's global type. This mirrors EntityQueryTypeProvider's
 * marker-only pattern and keeps real type resolution untouched.
 */
class ConfigObjectTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return KEY
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        val project = psiElement.project
        if (
            !project.drupalExtendSettings.isEnabled
            || DumbService.getInstance(project).isDumb
            || psiElement !is MethodReference
        ) {
            return null
        }

        val configName = configNameOf(psiElement) ?: return null

        return PhpType().add("#$KEY$SPLIT_KEY$configName")
    }

    override fun complete(expression: String, project: Project): PhpType? {
        // Marker is consumed by ConfigKeyCompletionProvider, never resolved to a class.
        return null
    }

    override fun getBySignature(
        expression: String,
        visited: Set<String?>?,
        depth: Int,
        project: Project?
    ): Collection<PhpNamedElement>? {
        return null
    }

    companion object {
        const val KEY = '㍀'
        const val SPLIT_KEY = '㍁'

        /**
         * Pure-PSI mirror of `Configs.isConfigNameCall` plus the literal first-argument check:
         * returns the config object name for a config-name call with a literal non-blank first
         * argument, null otherwise. Kept local so `getType` never reaches into the index.
         */
        fun configNameOf(methodReference: MethodReference): String? {
            val isConfigNameCall = when (methodReference.name) {
                "config" -> true
                "get", "getEditable" -> {
                    val receiverText = methodReference.classReference?.text?.lowercase() ?: return null
                    "configfactory" in receiverText || "config_factory" in receiverText || "config.factory" in receiverText
                }

                else -> false
            }
            if (!isConfigNameCall) return null

            val parameterList = methodReference.parameterList ?: return null
            val firstParameter = parameterList.getParameter(0) as? StringLiteralExpression ?: return null

            return firstParameter.contents.takeIf { it.isNotBlank() }
        }
    }
}
