package com.github.nvelychenko.drupalextend.lineMarker

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInsight.daemon.DefaultGutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.drupal.hooks.DrupalHooksUtils
import com.jetbrains.php.drupal.settings.DrupalDataService
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import icons.DrupalIcons


class DrupalHookDocLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        // The index is populated regardless of plugin settings — consumers gate themselves.
        if (!elements[0].project.drupalExtendSettings.isEnabled) return
        val dataService = DrupalDataService.getInstance(elements[0].project)
        if (!dataService.isEnabled || !dataService.isVersionValid) return

        for (element in elements) {
            handlePotentialDoc(element, result)
        }
    }

    private fun handlePotentialDoc(
        element: PsiElement,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val project = element.project

        val hookFunctionName = DrupalHooksUtils.getHookNameFromDocFunction(element) ?: return

        val instance = FileBasedIndex.getInstance()
        val attributes = mutableListOf<PhpPsiElement>()

        val phpIndex = PhpIndex.getInstance(project)
        val hookName = hookFunctionName.substringAfter("hook_")
        val values = instance.getValues(HOOK_ATTRIBUTES_INDEX_KEY, hookName, GlobalSearchScope.allScope(project))
        for (value in values) {
            phpIndex.getClassesByFQN(value)
                .takeIf { it.isNotEmpty() }
                ?.forEach {
                    getHookAttributesByName(it, hookName)
                        .forEach { attribute ->
                            val parent = attribute.parent.parent
                            if (parent is PhpPsiElement) {
                                attributes.add(parent)
                            }
                        }
                }

            if (attributes.isEmpty()) return
        }

        val anchor = PsiTreeUtil.getDeepestFirst(element)
        result.add(
            LineMarkerInfo(
                anchor,
                anchor.textRange,
                DrupalIcons.ImplementedHook,
                { "Maybe some day" },
                DefaultGutterIconNavigationHandler(
                    attributes,
                    "Choose Implementations"
                ),
                GutterIconRenderer.Alignment.LEFT,
                { "IM NOT USER FRIENDLY, OK?" }
            )
        )
    }
}
