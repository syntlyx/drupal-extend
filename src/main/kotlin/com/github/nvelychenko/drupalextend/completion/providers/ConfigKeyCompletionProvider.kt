package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.type.ConfigObjectTypeProvider
import com.github.nvelychenko.drupalextend.util.Configs
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * `\Drupal::config('system.site')->get('page.fr|')` completes the config object's key paths.
 *
 * The config name reaches us two ways:
 *  - direct chain: the receiver is itself a config-name call (`config(...)`/`get(...)` on a
 *    config factory) with a literal argument — read straight off the PSI, no type needed;
 *  - through a variable: ConfigObjectTypeProvider stamps a `#<KEY><SPLIT_KEY><name>` marker
 *    onto the receiver's type, which we read here off the raw local type.
 *
 * Index access lives only here (completion is smart-mode); we do NOT `stopHere`, so a `get()`
 * on a non-config receiver still falls through to other providers (e.g. entity field get()).
 */
class ConfigKeyCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val leaf = completionParameters.originalPosition ?: return
        val project = leaf.project

        if (!Configs.isSupportEnabled(project)) return

        val element = PsiTreeUtil.getParentOfType(leaf, StringLiteralExpression::class.java, false) ?: return

        val parameterList = element.parent as? ParameterList ?: return
        if (parameterList.parameters.firstOrNull()?.isEquivalentTo(element) != true) return

        val methodReference = parameterList.parent as? MethodReference ?: return
        if (methodReference.name != "get") return

        val configName = resolveConfigName(methodReference) ?: return

        val keyResultSet = completionResultSet.withPrefixMatcher(element.contents)
        Configs.keyPaths(project, configName)
            .forEach { keyResultSet.addElement(LookupElementBuilder.create(it)) }

        // Intentionally no stopHere(): other get() completions may still apply.
    }

    /**
     * The config object name for the receiver of `->get(...)`, or null when the receiver
     * is not a config object.
     */
    private fun resolveConfigName(methodReference: MethodReference): String? {
        val classReference = methodReference.classReference as? PhpReference ?: return null

        // Direct chain: receiver is itself a config-name call with a literal argument.
        (classReference as? MethodReference)
            ?.let { ConfigObjectTypeProvider.configNameOf(it) }
            ?.let { return it }

        // Through a variable: read the marker the type provider stamped onto the receiver.
        // Use the LOCAL type, not globalType — the platform collapses our unresolvable
        // `#`-marker to `?` once globalType resolves it, but the raw local type keeps it.
        return classReference.type.types
            .firstOrNull { it.startsWith("#${ConfigObjectTypeProvider.KEY}") }
            ?.substringAfter(ConfigObjectTypeProvider.SPLIT_KEY)
            ?.takeIf { it.isNotBlank() }
    }
}
