package com.github.nvelychenko.drupalextend.project

import com.intellij.codeInspection.ProblemHighlightType

/**
 * Per-rule severity for the Drupal code-style linter. The highlight type of every reported
 * problem is overridden with this value, so a rule can render as a warning or an error even
 * though all rules live under one umbrella inspection.
 */
enum class DrupalCodeStyleSeverity(val label: String) {
    OFF("Off"),
    WEAK_WARNING("Weak warning"),
    WARNING("Warning"),
    ERROR("Error");

    fun toProblemHighlightType(): ProblemHighlightType? {
        return when (this) {
            OFF -> null
            WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING
            WARNING -> ProblemHighlightType.WARNING
            ERROR -> ProblemHighlightType.GENERIC_ERROR
        }
    }

    override fun toString(): String = label

    companion object {
        fun fromState(name: String?, default: DrupalCodeStyleSeverity): DrupalCodeStyleSeverity {
            return entries.firstOrNull { it.name == name } ?: default
        }
    }
}
