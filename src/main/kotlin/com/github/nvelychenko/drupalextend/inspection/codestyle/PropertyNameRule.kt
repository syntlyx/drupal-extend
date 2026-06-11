package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList

internal object PropertyNameRule : DrupalCodeStyleRule {
    override val id: String = "property_name"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStylePropertyNameEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val fieldsList = element as? PhpClassFieldsList ?: return

        for (field in fieldsList.fields) {
            if (field.isConstant) continue

            val name: String? = field.name
            if (name.isNullOrBlank()) continue
            if (!name.isPropertyNameViolation()) continue

            val nameElement = field.nameNode?.psi ?: field
            context.register(
                DrupalCodeStyleProblem(
                    element = nameElement,
                    message = "Class property \$$name should use lowerCamelCase",
                    highlightType = ProblemHighlightType.WEAK_WARNING,
                    fixes = emptyList(),
                    includeFileFix = false,
                )
            )
        }
    }
}

/**
 * Returns true when a property name violates lowerCamelCase.
 *
 * A name is a violation if it:
 * - contains an underscore (snake_case or leading/trailing underscore), or
 * - starts with an uppercase letter.
 *
 * Single lowercase words such as "items" are valid and return false.
 */
internal fun String.isPropertyNameViolation(): Boolean {
    if (isEmpty()) return false
    if (this[0].isUpperCase()) return true
    if (contains('_')) return true
    return false
}
