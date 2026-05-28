package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.github.nvelychenko.drupalextend.util.normalizeRouteClassFqn
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import org.jetbrains.yaml.psi.YAMLFile

class RoutesYamlGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val project = element.project
        if (!Routes.isSupportEnabled(project)) return null

        controllerRouteParameterTargets(element)?.let { return it }

        return null
    }

    private fun controllerRouteParameterTargets(element: PsiElement): Array<PsiElement>? {
        val parameter = PsiTreeUtil.getParentOfType(element, Parameter::class.java, false) ?: return null
        val parameterName = parameter.name.removePrefix('$'.toString()).takeIf { it.isNotBlank() } ?: return null
        val method = PsiTreeUtil.getParentOfType(parameter, Method::class.java, false) ?: return null
        val phpClass = method.containingClass ?: return null
        val project = element.project
        val index = FileBasedIndex.getInstance()
        val handlerClass = phpClass.fqn.normalizeRouteClassFqn()
        val routeNames = index.getAllProjectKeys(ROUTES_INDEX_KEY, project)
            .filter { routeName ->
                val route = Routes.definition(project, routeName) ?: return@filter false
                route.handlerClass?.normalizeRouteClassFqn() == handlerClass
                        && route.handlerMethod == method.name
                        && parameterName in route.parameters
            }
        if (routeNames.isEmpty()) return null

        val psiManager = PsiManager.getInstance(project)
        val targets = mutableListOf<PsiElement>()
        routeNames.forEach { routeName ->
            index.processValues(
                ROUTES_INDEX_KEY,
                routeName,
                null,
                { file, _ ->
                    val yamlFile = psiManager.findFile(file) as? YAMLFile ?: return@processValues true
                    val routeDefinition = RoutingYaml.routeDefinition(yamlFile, routeName)
                        ?: return@processValues true

                    targets.add(routeDefinition.key ?: routeDefinition)
                    true
                },
                GlobalSearchScope.allScope(project)
            )
        }

        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
