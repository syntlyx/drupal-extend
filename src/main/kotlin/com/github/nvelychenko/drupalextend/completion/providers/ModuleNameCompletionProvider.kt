package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.reference.referenceProvider.ModuleNamePositionKind
import com.github.nvelychenko.drupalextend.reference.referenceProvider.moduleNamePositionKind
import com.github.nvelychenko.drupalextend.util.Modules
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Module machine-name completion in PHP. Offers names from the module index inside
 * `moduleHandler()->moduleExists('…')` / installer calls, `themeExists('…')`, and test
 * `protected static $modules = ['…']` arrays.
 *
 * Registered on a broad `StringLiteralExpression` pattern; narrows to a recognised position
 * itself and bails O(1) otherwise.
 */
class ModuleNameCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: completionParameters.position
        val project = leaf.project

        if (!Modules.isSupportEnabled(project)) return

        val literal = leaf.parent as? StringLiteralExpression ?: return
        val kind = literal.moduleNamePositionKind() ?: return

        // In a test `$modules` array only installable extensions belong (themes are enabled
        // differently); elsewhere offer everything the index knows.
        val installableOnly = kind == ModuleNamePositionKind.MODULES_FIELD

        Modules.allMachineNames(project).forEach { machineName ->
            val type = Modules.extensionType(project, machineName)
            if (installableOnly && type != null && type != "module" && type != "profile") return@forEach

            var lookupElement = LookupElementBuilder.create(machineName)
            if (!type.isNullOrBlank()) {
                lookupElement = lookupElement.withTypeText(type, true)
            }

            completionResultSet.addElement(lookupElement)
        }
    }
}
