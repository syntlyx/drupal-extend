package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.getRouteMethodReference
import com.github.nvelychenko.drupalextend.util.isRouteCall
import com.github.nvelychenko.drupalextend.util.routeParameterKeys
import com.github.nvelychenko.drupalextend.util.routeParametersArgument
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class RoutesPhpInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!Routes.isSupportEnabled(project)) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is StringLiteralExpression) return

                val routeName = element.contents.trim()
                if (routeName.isBlank()) return

                val methodReference = element.getRouteMethodReference() ?: return
                if (!methodReference.isRouteCall(project)) return
                val route = Routes.definition(project, routeName)
                if (route == null) {
                    holder.registerProblem(
                        element,
                        "Unknown route '$routeName'",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                    return
                }

                val requiredParameters = route.parameters
                if (requiredParameters.isEmpty()) return

                val routeParametersArgument = methodReference.routeParametersArgument() ?: return
                val existingParameters = routeParametersArgument.arrayArgument?.routeParameterKeys()
                if (routeParametersArgument.argument != null && existingParameters == null) return

                val missingParameters = requiredParameters.filterNot { it in (existingParameters ?: emptySet()) }
                if (missingParameters.isEmpty()) return

                holder.registerProblem(
                    element,
                    missingRouteParameterMessage(routeName, missingParameters),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    AddMissingRouteParametersQuickFix()
                )
            }
        }
    }

    private fun missingRouteParameterMessage(routeName: String, missingParameters: List<String>): String {
        val parameters = missingParameters.joinToString(", ") { "'$it'" }
        return if (missingParameters.size == 1) {
            "Missing route parameter $parameters for route '$routeName'"
        } else {
            "Missing route parameters $parameters for route '$routeName'"
        }
    }

    private class AddMissingRouteParametersQuickFix : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Add missing route parameters"
        }

        override fun getName(): String {
            return "Add missing route parameters"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val routeString = descriptor.psiElement as? StringLiteralExpression ?: return
            val routeName = routeString.contents.trim()
            val route = Routes.definition(project, routeName) ?: return
            val methodReference = routeString.getRouteMethodReference() ?: return
            val routeParametersArgument = methodReference.routeParametersArgument() ?: return
            val existingParameters = routeParametersArgument.arrayArgument?.routeParameterKeys().orEmpty()
            val missingParameters = route.parameters.filterNot { it in existingParameters }
            if (missingParameters.isEmpty()) return

            val parameterList = routeParametersArgument.parameterList
            val arguments = parameterList.parameters.map { it.text }.toMutableList()
            val routeParametersIndex = routeParametersArgument.parameterIndex
            val entries = missingParameters.joinToString(", ") { routeParameterEntry(it) }

            if (arguments.size <= routeParametersIndex) {
                while (arguments.size < routeParametersIndex) {
                    arguments.add("[]")
                }
                arguments.add("[$entries]")
            } else {
                val arrayArgument = routeParametersArgument.arrayArgument ?: return
                arguments[routeParametersIndex] = addEntriesToArrayText(arrayArgument, entries)
            }

            val newParameterList = PhpPsiElementFactory.createArgumentList(project, arguments.joinToString(", "))
            parameterList.replace(newParameterList)
        }

        private fun addEntriesToArrayText(arrayArgument: ArrayCreationExpression, entries: String): String {
            val text = arrayArgument.text.trim()
            return when {
                text == "[]" -> "[$entries]"
                text == "array()" -> "array($entries)"
                text.startsWith("[") && text.endsWith("]") -> {
                    text.dropLast(1) + if (arrayArgument.hashElements.iterator().hasNext()) ", $entries]" else "$entries]"
                }

                text.startsWith("array(") && text.endsWith(")") -> {
                    text.dropLast(1) + if (arrayArgument.hashElements.iterator().hasNext()) ", $entries)" else "$entries)"
                }

                else -> text
            }
        }

        private fun routeParameterEntry(parameter: String): String {
            return "'$parameter' => " + '$' + parameter
        }
    }
}
