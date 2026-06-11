package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.github.nvelychenko.drupalextend.php.edit.escapePhpSingleQuoted
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RouteParametersArgument
import com.github.nvelychenko.drupalextend.util.getRouteMethodReference
import com.github.nvelychenko.drupalextend.util.isRouteCall
import com.github.nvelychenko.drupalextend.util.routeParameterKeys
import com.github.nvelychenko.drupalextend.util.routeParametersArgument
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
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
            val psiFile = methodReference.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

            // A single targeted insertion at a PSI-derived offset: the other arguments (their
            // formatting, line breaks, and comments) are never rewritten.
            val entries = missingParameters.joinToString(", ") { routeParameterEntry(it) }
            val edit = routeParametersArgument.arrayArgument
                ?.let { entriesInsertionEdit(it, entries, document) }
                ?: newArgumentInsertionEdit(routeParametersArgument, entries)
                ?: return

            listOf(edit).applyTo(document)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        /** Appends `, [entries]` (plus `[]` fillers for skipped positions) after the last argument. */
        private fun newArgumentInsertionEdit(argument: RouteParametersArgument, entries: String): TextEdit? {
            // A non-array argument at the parameters position can't be extended safely.
            if (argument.argument != null) return null
            val parameters = argument.parameterList.parameters
            val lastParameter = parameters.lastOrNull() ?: return null
            val fillers = ", []".repeat((argument.parameterIndex - parameters.size).coerceAtLeast(0))

            return TextEdit(lastParameter.textRange.endOffset, "$fillers, [$entries]")
        }

        /**
         * Inserts the entries inside the existing array argument before its closing
         * bracket/paren. The offsets come from PSI, so `[...]`, `array(...)`, and any
         * spacing/case variant work alike, and brackets inside strings or comments can
         * never confuse the insertion point.
         */
        private fun entriesInsertionEdit(
            array: ArrayCreationExpression,
            entries: String,
            document: Document,
        ): TextEdit? {
            val closeOffset = array.textRange.endOffset - 1
            if (closeOffset < array.textRange.startOffset) return null
            val lastValue = array.children.lastOrNull()
                ?: return TextEdit(closeOffset, entries)
            val afterLastValue = lastValue.textRange.endOffset
            val trailingCommaIndex = document.charsSequence
                .subSequence(afterLastValue, closeOffset)
                .indexOf(',')
            val multiline = document.getLineNumber(array.textRange.startOffset) != document.getLineNumber(closeOffset)

            if (!multiline) {
                return TextEdit(closeOffset, if (trailingCommaIndex >= 0) " $entries" else ", $entries")
            }

            val indent = document.lineIndentAt(lastValue.textRange.startOffset)
            return if (trailingCommaIndex >= 0) {
                TextEdit(afterLastValue + trailingCommaIndex + 1, "\n$indent$entries,")
            } else {
                TextEdit(afterLastValue, ",\n$indent$entries")
            }
        }

        private fun Document.lineIndentAt(offset: Int): String {
            val line = getLineNumber(offset)

            return charsSequence
                .subSequence(getLineStartOffset(line), getLineEndOffset(line))
                .takeWhile { it == ' ' || it == '\t' }
                .toString()
        }

        private fun routeParameterEntry(parameter: String): String {
            // Route placeholder names are identifier-like, but never trust that for the quoted
            // key, and keep the suggested variable a valid PHP identifier.
            val variableName = parameter.replace(NON_IDENTIFIER_CHARS, "_")

            return "'${parameter.escapePhpSingleQuoted()}' => " + '$' + variableName
        }
    }
}

private val NON_IDENTIFIER_CHARS = Regex("[^A-Za-z0-9_]")
