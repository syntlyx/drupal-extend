package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.util.Links
import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.github.nvelychenko.drupalextend.util.normalizeRouteClassFqn
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class RoutesYamlInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!Routes.isSupportEnabled(project)) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor
        if (!RoutingYaml.isRouteReferenceFileName(holder.file.virtualFile?.name)) return emptyVisitor

        val localRouteNames by lazy { getLocalRouteNames(holder.file as? YAMLFile) }
        val localLinkIds by lazy { getLocalLinkIds(holder.file as? YAMLFile) }

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is YAMLScalar || RoutingYaml.isKey(element)) return

                inspectRouteReference(element, localRouteNames, holder)
                inspectLinkParent(element, localLinkIds, holder)
                inspectPermissionReference(element, holder)
                inspectRouteHandler(element, holder)
            }
        }
    }

    private fun inspectRouteReference(
        scalar: YAMLScalar,
        localRouteNames: List<String>,
        holder: ProblemsHolder
    ) {
        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return
        if (!RoutingYaml.isRouteReferenceKeyPath(keyPath)) return

        val routeName = scalar.textValue.trim()
        if (routeName.isBlank()) return
        if (routeExists(routeName, localRouteNames, holder)) return

        holder.registerProblem(
            scalar,
            "Unknown route '$routeName'",
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
        )
    }

    private fun inspectLinkParent(
        scalar: YAMLScalar,
        localLinkIds: List<String>,
        holder: ProblemsHolder
    ) {
        if (!RoutingYaml.isLinksFileName(scalar.containingFile.virtualFile?.name)) return

        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return
        if (!RoutingYaml.isLinkParentKeyPath(keyPath)) return

        val linkId = scalar.textValue.trim()
        if (linkId.isBlank()) return
        if (linkId.contains(":")) return
        if (linkId in localLinkIds || Links.exists(holder.project, linkId)) return

        holder.registerProblem(
            scalar,
            "Unknown link parent '$linkId'",
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
        )
    }

    private fun inspectPermissionReference(
        scalar: YAMLScalar,
        holder: ProblemsHolder
    ) {
        if (!Permissions.isYamlPermissionReferenceFileName(scalar.containingFile.virtualFile?.name)) return

        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return
        if (!Permissions.isYamlPermissionKeyPath(keyPath)) return

        Permissions.permissionTokens(scalar)
            .filterNot { Permissions.exists(holder.project, it.name) }
            .forEach {
                holder.registerProblem(
                    scalar,
                    "Unknown permission '${it.name}'",
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                    it.range
                )
            }
    }

    private fun inspectRouteHandler(
        scalar: YAMLScalar,
        holder: ProblemsHolder
    ) {
        if (!RoutingYaml.isRoutingFileName(scalar.containingFile.virtualFile?.name)) return

        val keyValue = scalar.parent as? YAMLKeyValue ?: return
        if (!RoutingYaml.isRouteHandlerKeyPath(keyValue.keyPath)) return

        val textValue = scalar.textValue.trim()
        val handler = RoutingYaml.routeHandlerFromText(textValue) ?: return
        val phpClass = PhpIndex.getInstance(holder.project)
            .getClassesByFQN(handler.className.normalizeRouteClassFqn())
            .firstOrNull()

        if (phpClass == null) {
            holder.registerProblem(
                scalar,
                "Unknown route handler class '${handler.className}'",
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                RoutingYaml.handlerValueTextRange(scalar, textValue, handler)
            )
            return
        }

        val methodName = handler.methodName ?: return
        val method = phpClass.findMethodByName(methodName)
        if (method != null) {
            inspectControllerRouteParameters(scalar, keyValue, method, holder)
            return
        }

        holder.registerProblem(
            scalar,
            "Unknown route handler method '${handler.className}::$methodName'",
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            RoutingYaml.handlerValueTextRange(scalar, textValue, handler)
        )
    }

    private fun inspectControllerRouteParameters(
        scalar: YAMLScalar,
        handlerKeyValue: YAMLKeyValue,
        method: Method,
        holder: ProblemsHolder
    ) {
        if (handlerKeyValue.keyText != "_controller") return

        val routeDefinition = routeDefinitionFor(handlerKeyValue) ?: return
        val routeParameters = RoutingYaml.routePathParameters(routeDefinition)
        if (routeParameters.isEmpty()) return

        val methodParameters = method.parameters.associateBy { it.routeParameterName() }
        val missingParameters = routeParameters.filterNot { methodParameters.containsKey(it) }
        if (missingParameters.isNotEmpty()) {
            holder.registerProblem(
                scalar,
                missingControllerParameterMessage(routeDefinition.keyText, missingParameters),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }

        routeParameters
            .mapNotNull { routeParameter ->
                val methodParameter = methodParameters[routeParameter] ?: return@mapNotNull null
                val expectedClass = expectedEntityClass(holder.project, routeParameter) ?: return@mapNotNull null
                val actualClass = actualControllerParameterClass(holder.project, methodParameter) ?: return@mapNotNull null
                if (isControllerParameterTypeCompatible(holder.project, expectedClass, actualClass)) return@mapNotNull null

                routeParameter to (expectedClass to actualClass)
            }
            .forEach { (routeParameter, classes) ->
                val (expectedClass, actualClass) = classes
                holder.registerProblem(
                    scalar,
                    "Route parameter '$routeParameter' expects '${expectedClass.toDisplayFqn()}', '${actualClass.toDisplayFqn()}' provided",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
    }

    private fun missingControllerParameterMessage(routeName: String, missingParameters: List<String>): String {
        val parameters = missingParameters.joinToString(", ") { "'${it.toPhpVariableName()}'" }
        return if (missingParameters.size == 1) {
            "Missing controller parameter $parameters for route '$routeName'"
        } else {
            "Missing controller parameters $parameters for route '$routeName'"
        }
    }

    private fun routeDefinitionFor(element: PsiElement): YAMLKeyValue? {
        var current = element.parent
        while (current != null) {
            if (current is YAMLKeyValue && RoutingYaml.isRouteDefinition(current)) return current
            current = current.parent
        }

        return null
    }

    private fun expectedEntityClass(project: Project, routeParameter: String): String? {
        val index = FileBasedIndex.getInstance()
        return index.getValue(CONTENT_ENTITY_INDEX_KEY, routeParameter, project)?.fqn
            ?: index.getValue(CONFIG_ENTITY_INDEX_KEY, routeParameter, project)
    }

    private fun actualControllerParameterClass(project: Project, parameter: Parameter): String? {
        return parameter.declaredType
            .global(project)
            .filterUnknown()
            .filterNull()
            .types
            .asSequence()
            .map { it.normalizeRouteClassFqn() }
            .filterNot { PhpType.isPrimitiveType(it) }
            .firstOrNull()
    }

    private fun isControllerParameterTypeCompatible(project: Project, expectedClass: String, actualClass: String): Boolean {
        val phpIndex = PhpIndex.getInstance(project)
        val expected = expectedClass.normalizeRouteClassFqn()
        val actual = actualClass.normalizeRouteClassFqn()
        return PhpType.findSuper(actual, expected, phpIndex) || PhpType.findSuper(expected, actual, phpIndex)
    }

    private fun routeExists(
        routeName: String,
        localRouteNames: List<String>,
        holder: ProblemsHolder
    ): Boolean {
        return routeName in localRouteNames || Routes.exists(holder.project, routeName)
    }

    private fun getLocalRouteNames(yamlFile: YAMLFile?): List<String> {
        if (yamlFile == null || !RoutingYaml.isRoutingFileName(yamlFile.virtualFile?.name)) return emptyList()

        return RoutingYaml.routeDefinitions(yamlFile).map { it.keyText }
    }

    private fun getLocalLinkIds(yamlFile: YAMLFile?): List<String> {
        if (yamlFile == null || !RoutingYaml.isLinksFileName(yamlFile.virtualFile?.name)) return emptyList()

        return RoutingYaml.linkDefinitions(yamlFile).map { it.keyText }
    }

    private fun Parameter.routeParameterName(): String {
        return name.removePrefix('$'.toString())
    }

    private fun String.toPhpVariableName(): String {
        return '$'.toString() + this
    }

    private fun String.toDisplayFqn(): String {
        return normalizeRouteClassFqn().removePrefix("\\")
    }
}
