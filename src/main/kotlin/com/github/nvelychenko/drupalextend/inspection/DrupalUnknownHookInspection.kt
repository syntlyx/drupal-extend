package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.extensions.getModificationTrackerForIndexId
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.drupal.hooks.DrupalHooksIndex
import com.jetbrains.php.drupal.settings.DrupalDataService
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.stubs.indexes.PhpFunctionNameIndex

/**
 * Flags `#[Hook('name')]` attributes whose hook name matches no known Drupal hook.
 *
 * Known hooks come from two sources: the `com.jetbrains.php.drupal` [DrupalHooksIndex]
 * (so this is registered in `drupal.xml`, never `plugin.xml`) and `hook_*` functions
 * declared in `*.api.php` docs ([PhpFunctionNameIndex]).
 *
 * Hot-path discipline: the per-attribute work is a single set lookup plus, only when that
 * misses, a scan of a small precompiled regex list. Both the known-name set and the dynamic
 * pattern list are built once per project and cached against the index modification trackers,
 * so no index iteration happens per element.
 */
class DrupalUnknownHookInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!project.drupalExtendSettings.isEnabled) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is PhpAttribute) return
                if (element.fqn != KnownHooks.HOOK_ATTRIBUTE_FQN) return

                // A bare `#[Hook]` or a non-string / interpolated first argument is normal
                // mid-typing PHP: never warn on it.
                val literal = element.parameters.firstOrNull() as? StringLiteralExpression ?: return
                if (literal.firstPsiChild != null) return
                val hookName = literal.contents
                if (hookName.isEmpty()) return

                if (KnownHooks.isKnownHook(project, hookName)) return

                holder.registerProblem(
                    literal,
                    "Unknown hook '$hookName'",
                    ProblemHighlightType.WEAK_WARNING
                )
            }
        }
    }
}

/**
 * Project-wide known-hook lookup, kept in an [object] so the [CachedValueProvider] passed to
 * [CachedValuesManager] captures only [Project] — never a per-pass inspection instance, which
 * the platform rejects as "same CachedValue with different captured context".
 */
private object KnownHooks {
    const val HOOK_ATTRIBUTE_FQN = "\\Drupal\\Core\\Hook\\Attribute\\Hook"
    private const val HOOK_FUNCTION_PREFIX = "hook_"

    // One-or-more identifier chars; bounded inside the anchored full-name pattern.
    private const val PLACEHOLDER_REGEX = "[A-Za-z0-9_]+"

    private val CACHE_KEY: Key<CachedValue<Data>> =
        Key.create("com.github.nvelychenko.drupalextend.inspection.DrupalUnknownHookInspection.knownHooks")

    fun isKnownHook(project: Project, hookName: String): Boolean {
        val data = cached(project)
        if (hookName in data.exactNames) return true
        return data.dynamicPatterns.any { it.matches(hookName) }
    }

    private fun cached(project: Project): Data {
        return CachedValuesManager.getManager(project).getCachedValue(project, CACHE_KEY, {
            val index = FileBasedIndex.getInstance()
            val exactNames = hashSetOf<String>()

            collectFromDrupalHooksIndex(project, index, exactNames)
            collectFromApiDocFunctions(project, index, exactNames)

            val dynamicPatterns = exactNames
                .filter { containsPlaceholderSegment(it) }
                .map { hookNameToPattern(it) }

            CachedValueProvider.Result.create(
                Data(exactNames, dynamicPatterns),
                // DrupalHooksIndex also feeds api.php hooks, but PhpFunctionNameIndex is the
                // source we always have; track it so api.php edits invalidate the cache.
                getModificationTrackerForIndexId(project, PhpFunctionNameIndex.KEY, index)
            )
        }, false)
    }

    private fun collectFromDrupalHooksIndex(
        project: Project,
        index: FileBasedIndex,
        exactNames: MutableSet<String>,
    ) {
        val scope = GlobalSearchScope.allScope(project)
        try {
            // DrupalDataService is declared non-null but is genuinely absent when the optional
            // php.drupal plugin isn't loaded (e.g. tests): treat it as nullable.
            @Suppress("UNNECESSARY_SAFE_CALL", "SAFE_CALL_WILL_CHANGE_NULLABILITY")
            val version = DrupalDataService.getInstance(project)?.takeIf { it.isEnabled }?.version ?: return
            index.processAllKeys(DrupalHooksIndex.KEY, { key ->
                ProgressManager.checkCanceled()
                if (key.suits(version)) {
                    val hasValueInScope =
                        !index.processValues(DrupalHooksIndex.KEY, key, null, { _, _ -> false }, scope)
                    if (hasValueInScope) {
                        exactNames.add(key.name)
                    }
                }
                true
            }, project)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            // The optional php.drupal index may be unavailable (e.g. in tests, or before the
            // plugin's index is registered/built). It surfaces as IndexNotReadyException, an
            // "index is not registered" IllegalArgumentException, etc. Fail soft to the api.php
            // source rather than throwing into the IDE log.
            if (e is IndexNotReadyException || e is IllegalStateException || e is IllegalArgumentException) {
                return
            }
            throw e
        }
    }

    private fun collectFromApiDocFunctions(
        project: Project,
        index: FileBasedIndex,
        exactNames: MutableSet<String>,
    ) {
        index.processAllKeys(PhpFunctionNameIndex.KEY, { functionName ->
            ProgressManager.checkCanceled()
            if (functionName.startsWith(HOOK_FUNCTION_PREFIX)) {
                exactNames.add(functionName.substring(HOOK_FUNCTION_PREFIX.length))
            }
            true
        }, project)
    }

    /**
     * api.php doc hooks carry UPPERCASE placeholder segments (`hook_form_FORM_ID_alter`,
     * `hook_ENTITY_TYPE_presave`, `hook_preprocess_HOOK`). Each all-uppercase underscore
     * segment becomes a `[A-Za-z0-9_]+` wildcard, so `form_node_edit_form_alter` matches the
     * known pattern `form_FORM_ID_alter`.
     */
    private fun hookNameToPattern(hookName: String): Regex {
        val regex = hookName.split('_').joinToString("_") { segment ->
            if (isPlaceholderSegment(segment)) PLACEHOLDER_REGEX else Regex.escape(segment)
        }
        return Regex(regex)
    }

    private fun containsPlaceholderSegment(hookName: String): Boolean =
        hookName.split('_').any { isPlaceholderSegment(it) }

    private fun isPlaceholderSegment(segment: String): Boolean =
        segment.isNotEmpty() && segment.any { it.isLetter() } && segment.none { it.isLowerCase() }

    private data class Data(
        val exactNames: Set<String>,
        val dynamicPatterns: List<Regex>,
    )
}
