package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.util.ServiceTags
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

class ServicesYamlInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!Services.isSupportEnabled(project)) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor
        if (!ServicesYaml.isServicesFileName(holder.file.virtualFile?.name)) return emptyVisitor

        val localServiceIds by lazy { getLocalServiceIds(holder.file as? YAMLFile) }
        val localServiceClasses by lazy { ServiceArgumentMatching.localServiceClasses(holder.file as? YAMLFile) }
        val duplicatedServiceIds by lazy {
            localServiceIds
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        }

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is YAMLKeyValue -> inspectServiceDefinition(element, duplicatedServiceIds, localServiceClasses, holder)
                    is YAMLScalar -> inspectScalar(element, localServiceIds, holder)
                }
            }
        }
    }

    private fun inspectServiceDefinition(
        keyValue: YAMLKeyValue,
        duplicatedServiceIds: Set<String>,
        localServiceClasses: Map<String, String>,
        holder: ProblemsHolder
    ) {
        if (!ServicesYaml.isServiceDefinition(keyValue)) return
        if (duplicatedServiceIds.contains(keyValue.keyText)) {
            holder.registerProblem(
                keyValue.key ?: keyValue,
                "Duplicate service id '${keyValue.keyText}'",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }

        inspectConstructorArguments(keyValue, localServiceClasses, holder)
    }

    private fun inspectScalar(
        scalar: YAMLScalar,
        localServiceIds: List<String>,
        holder: ProblemsHolder
    ) {
        if (ServicesYaml.isKey(scalar)) return

        inspectTaggedArgumentTag(scalar, holder)
        inspectServiceReference(scalar, localServiceIds, holder)
        inspectPlainServiceId(scalar, localServiceIds, holder)
        inspectServiceClass(scalar, holder)
    }

    private fun inspectTaggedArgumentTag(
        scalar: YAMLScalar,
        holder: ProblemsHolder
    ) {
        val tagName = ServicesYaml.consumedTagName(scalar) ?: return
        if (ServiceTags.isKnown(holder.project, tagName)) return

        holder.registerProblem(
            scalar,
            "Unknown service tag '$tagName'",
            ProblemHighlightType.WEAK_WARNING,
            ServicesYaml.scalarTextValueRange(scalar, tagName)
        )
    }

    private fun inspectServiceReference(
        scalar: YAMLScalar,
        localServiceIds: List<String>,
        holder: ProblemsHolder
    ) {
        val textValue = scalar.textValue.trim()
        val serviceId = ServicesYaml.serviceIdFromReferenceValue(textValue) ?: return
        if (textValue.startsWith("@?")) return
        if (serviceExists(serviceId, localServiceIds, holder)) return

        holder.registerProblem(
            scalar,
            "Unknown service '$serviceId'",
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            ServicesYaml.referenceValueTextRange(scalar, textValue, serviceId)
        )
    }

    private fun inspectPlainServiceId(
        scalar: YAMLScalar,
        localServiceIds: List<String>,
        holder: ProblemsHolder
    ) {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return
        if (!ServicesYaml.isPlainServiceIdKeyPath(keyValue.keyPath)) return

        val serviceId = scalar.textValue.trim()
        if (serviceId.isBlank()) return
        if (serviceExists(serviceId, localServiceIds, holder)) return

        holder.registerProblem(
            scalar,
            "Unknown service '$serviceId'",
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
        )
    }

    private fun inspectServiceClass(
        scalar: YAMLScalar,
        holder: ProblemsHolder
    ) {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return
        if (keyValue.keyText != "class") return
        if (!keyValue.keyPath.startsWith("services.")) return

        val classFqn = scalar.textValue.trim()
        if (classFqn.isBlank() || classFqn.contains("%")) return

        if (PhpIndex.getInstance(holder.project).getAnyByFQN(classFqn.normalizeServiceClassFqn()).isNotEmpty()) return

        holder.registerProblem(
            scalar,
            "Unknown class '$classFqn'",
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
        )
    }

    private fun inspectConstructorArguments(
        serviceDefinition: YAMLKeyValue,
        localServiceClasses: Map<String, String>,
        holder: ProblemsHolder
    ) {
        if (ServicesYaml.hasServiceOption(serviceDefinition, "factory")) return
        if (ServicesYaml.hasServiceOption(serviceDefinition, "parent")) return

        val serviceId = serviceDefinition.keyText
        val classFqn = ServicesYaml.serviceClass(serviceDefinition)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("%") }
            ?: return
        val phpClass = PhpIndex.getInstance(holder.project)
            .getClassesByFQN(classFqn.normalizeServiceClassFqn())
            .firstOrNull()
            ?: return
        val constructor = phpClass.constructor ?: return
        val constructorParameters = constructor.parameters.toList()
        if (constructorParameters.isEmpty()) return

        val arguments = ServicesYaml.serviceArguments(serviceDefinition)
        inspectConstructorArgumentCount(serviceDefinition, serviceId, constructor, arguments, holder)
        inspectConstructorArgumentTypes(serviceId, constructorParameters, arguments, localServiceClasses, holder)
    }

    private fun inspectConstructorArgumentCount(
        serviceDefinition: YAMLKeyValue,
        serviceId: String,
        constructor: Method,
        arguments: List<ServicesYaml.ServiceArgument>,
        holder: ProblemsHolder
    ) {
        val constructorParameters = constructor.parameters.toList()
        val requiredParameters = constructorParameters.filterNot { it.isOptional || it.isVariadic }
        if (arguments.size < requiredParameters.size) {
            val missingParameters = requiredParameters.drop(arguments.size).map { it.phpVariableName() }
            holder.registerProblem(
                serviceDefinition.key ?: serviceDefinition,
                missingConstructorArgumentMessage(serviceId, missingParameters),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                FillConstructorArgumentsQuickFix()
            )
        }

        if (constructorParameters.none { it.isVariadic } && arguments.size > constructorParameters.size) {
            holder.registerProblem(
                serviceDefinition.key ?: serviceDefinition,
                "Too many constructor arguments for service '$serviceId': expected ${constructorParameters.size}, got ${arguments.size}",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }
    }

    private fun inspectConstructorArgumentTypes(
        serviceId: String,
        constructorParameters: List<Parameter>,
        arguments: List<ServicesYaml.ServiceArgument>,
        localServiceClasses: Map<String, String>,
        holder: ProblemsHolder
    ) {
        val project = holder.project
        arguments
            .take(constructorParameters.size)
            .forEachIndexed { index, argument ->
                val referencedServiceId = argument.textValue?.let(ServicesYaml::serviceIdFromReferenceValue)
                    ?: return@forEachIndexed
                val actualClass = ServiceArgumentMatching.serviceClass(project, referencedServiceId, localServiceClasses)
                    ?: return@forEachIndexed
                val parameter = constructorParameters[index]
                val expectedClasses = ServiceArgumentMatching.constructorParameterClasses(project, parameter)
                if (expectedClasses.isEmpty()) return@forEachIndexed
                if (expectedClasses.any { ServiceArgumentMatching.isCompatible(project, it, actualClass) }) return@forEachIndexed
                val quickFixes = ServiceArgumentMatching.replacementServiceIds(
                    project = project,
                    expectedClasses = expectedClasses,
                    excludedServiceIds = setOf(serviceId, referencedServiceId),
                    localServiceClasses = localServiceClasses
                )
                    .map { ReplaceServiceArgumentQuickFix(it) }
                    .toTypedArray<LocalQuickFix>()

                holder.registerProblem(
                    argument.value,
                    "Constructor argument '${parameter.phpVariableName()}' for service '$serviceId' expects '${expectedClasses.first().toDisplayFqn()}', '${actualClass.toDisplayFqn()}' provided",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    *quickFixes
                )
            }
    }

    private fun missingConstructorArgumentMessage(serviceId: String, missingParameters: List<String>): String {
        val parameters = missingParameters.joinToString(", ") { "'$it'" }
        return if (missingParameters.size == 1) {
            "Missing constructor argument $parameters for service '$serviceId'"
        } else {
            "Missing constructor arguments $parameters for service '$serviceId'"
        }
    }

    private fun serviceExists(
        serviceId: String,
        localServiceIds: List<String>,
        holder: ProblemsHolder
    ): Boolean {
        return Services.exists(holder.project, serviceId, localServiceIds)
    }

    private fun getLocalServiceIds(yamlFile: YAMLFile?): List<String> {
        val servicesMapping = ServicesYaml.servicesMapping(PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java))
            ?: return emptyList()

        return servicesMapping.keyValues
            .filter { ServicesYaml.isServiceDefinition(it) }
            .map { it.keyText }
    }

    private fun Parameter.phpVariableName(): String {
        return '$'.toString() + name.removePrefix('$'.toString())
    }

    private fun String.toDisplayFqn(): String {
        return normalizeServiceClassFqn().removePrefix("\\")
    }

    private class ReplaceServiceArgumentQuickFix(
        private val serviceId: String,
    ) : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Replace service argument"
        }

        override fun getName(): String {
            return "Replace with '@$serviceId'"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val scalar = descriptor.psiElement as? YAMLScalar ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(scalar.containingFile) ?: return
            document.replaceString(
                scalar.textRange.startOffset,
                scalar.textRange.endOffset,
                scalar.replacementServiceReference(serviceId)
            )
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        private fun YAMLScalar.replacementServiceReference(serviceId: String): String {
            val optionalPrefix = if (textValue.trim().startsWith("@?")) "@?" else "@"
            return "'$optionalPrefix${serviceId.escapeYamlSingleQuoted()}'"
        }

        private fun String.escapeYamlSingleQuoted(): String {
            return replace("'", "''")
        }
    }

    /**
     * Appends the constructor arguments missing from a service's `arguments:` list, matching each
     * missing parameter to a project service by its declared type via the shared candidate ranking
     * ([ServiceArgumentMatching.bestServiceId] — exact class match first, then shortest id).
     * Unmatched parameters (scalars, or types no service provides) get a `'TODO'` placeholder plus
     * a one-time trailing comment on the `arguments:` line. Re-reads the live argument count in
     * [applyFix] so applying it twice — or after the gap was filled by hand — is a no-op.
     */
    private class FillConstructorArgumentsQuickFix : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Fill constructor arguments"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val serviceDefinition = ServicesYaml.serviceDefinitionFromKeyElement(descriptor.psiElement) ?: return
            if (ServicesYaml.hasServiceOption(serviceDefinition, "factory")) return
            if (ServicesYaml.hasServiceOption(serviceDefinition, "parent")) return

            val serviceId = serviceDefinition.keyText
            val classFqn = ServicesYaml.serviceClass(serviceDefinition)
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("%") }
                ?: return
            val phpClass = PhpIndex.getInstance(project)
                .getClassesByFQN(classFqn.normalizeServiceClassFqn())
                .firstOrNull()
                ?: return
            val constructor = phpClass.constructor ?: return
            val fillableParameters = constructor.parameters.filterNot { it.isVariadic }

            val arguments = ServicesYaml.serviceArguments(serviceDefinition)
            // Idempotency / fail-soft: nothing to add once the positional gap is filled.
            if (arguments.size >= fillableParameters.size) return

            val localServiceClasses = ServiceArgumentMatching.localServiceClasses(serviceDefinition.containingFile as? YAMLFile)
            val newEntries = fillableParameters.drop(arguments.size).map { parameter ->
                val expectedClasses = ServiceArgumentMatching.constructorParameterClasses(project, parameter)
                expectedClasses
                    .takeIf { it.isNotEmpty() }
                    ?.let { ServiceArgumentMatching.bestServiceId(project, it, serviceId, localServiceClasses) }
                    ?.let { "@$it" }
            }

            val document = PsiDocumentManager.getInstance(project).getDocument(serviceDefinition.containingFile) ?: return
            applyEntries(project, document, serviceDefinition, newEntries)
        }

        private fun applyEntries(
            project: Project,
            document: Document,
            serviceDefinition: YAMLKeyValue,
            newEntries: List<String?>
        ) {
            val serviceMapping = serviceDefinition.value as? YAMLMapping ?: return
            val argumentsKeyValue = serviceMapping.keyValues.firstOrNull { it.keyText == "arguments" }
            val usesPlaceholder = newEntries.any { it == null }

            if (argumentsKeyValue == null) {
                insertArgumentsKey(project, document, serviceMapping, newEntries, usesPlaceholder)
                return
            }

            when (val argumentsValue = argumentsKeyValue.value) {
                is YAMLSequence -> {
                    if (argumentsValue.text.trimStart().startsWith("[")) {
                        appendToInlineList(project, document, argumentsValue, newEntries)
                    } else {
                        appendToBlockList(project, document, argumentsKeyValue, argumentsValue, newEntries)
                    }
                }

                else -> return
            }

            if (usesPlaceholder) {
                appendTodoComment(project, document, argumentsKeyValue)
            }
        }

        private fun insertArgumentsKey(
            project: Project,
            document: Document,
            serviceMapping: YAMLMapping,
            newEntries: List<String?>,
            usesPlaceholder: Boolean
        ) {
            val classKeyValue = serviceMapping.keyValues.firstOrNull { it.keyText == "class" }
                ?: serviceMapping.keyValues.firstOrNull()
                ?: return
            val indent = lineIndentOf(document, classKeyValue.textRange.startOffset)
            val inlineList = newEntries.joinToString(", ") { yamlScalar(it) }
            val comment = if (usesPlaceholder) "  # TODO: provide remaining arguments" else ""
            val lineEndOffset = document.getLineEndOffset(document.getLineNumber(classKeyValue.textRange.endOffset))
            document.insertString(lineEndOffset, "\n${indent}arguments: [$inlineList]$comment")
            commit(project, document)
        }

        private fun appendToInlineList(
            project: Project,
            document: Document,
            sequence: YAMLSequence,
            newEntries: List<String?>
        ) {
            val closingRelative = sequence.text.lastIndexOf(']')
            if (closingRelative < 0) return
            val closingOffset = sequence.textRange.startOffset + closingRelative

            val hasExistingItems = sequence.items.any { it.value != null }
            val prefix = if (hasExistingItems) ", " else ""
            val addition = prefix + newEntries.joinToString(", ") { yamlScalar(it) }
            document.insertString(closingOffset, addition)
            commit(project, document)
        }

        private fun appendToBlockList(
            project: Project,
            document: Document,
            argumentsKeyValue: YAMLKeyValue,
            sequence: YAMLSequence,
            newEntries: List<String?>
        ) {
            val lastItem = sequence.items.lastOrNull()
            val itemIndent = if (lastItem != null) {
                lineIndentOf(document, lastItem.textRange.startOffset)
            } else {
                lineIndentOf(document, argumentsKeyValue.textRange.startOffset) + "  "
            }
            val anchorOffset = (lastItem ?: argumentsKeyValue).textRange.endOffset
            val insertionLineEnd = document.getLineEndOffset(document.getLineNumber(anchorOffset))
            val addition = newEntries.joinToString("") { "\n$itemIndent- ${yamlScalar(it)}" }
            document.insertString(insertionLineEnd, addition)
            commit(project, document)
        }

        private fun appendTodoComment(
            project: Project,
            document: Document,
            argumentsKeyValue: YAMLKeyValue
        ) {
            val lineNumber = document.getLineNumber(argumentsKeyValue.textRange.startOffset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            if (lineText.contains("# TODO: provide remaining arguments")) return

            document.insertString(lineEnd, "  # TODO: provide remaining arguments")
            commit(project, document)
        }

        private fun yamlScalar(serviceReference: String?): String {
            val literal = serviceReference ?: "TODO"
            return "'${literal.replace("'", "''")}'"
        }

        private fun lineIndentOf(document: Document, offset: Int): String {
            val lineNumber = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineText = document.getText(TextRange(lineStart, document.getLineEndOffset(lineNumber)))
            return lineText.takeWhile { it == ' ' }
        }

        private fun commit(project: Project, document: Document) {
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}

/**
 * Shared service-argument matching used by both the type-mismatch fix and the fill-arguments fix.
 * The candidate ranking is identical for both: a service whose class is an exact match for an
 * expected type sorts first, then by shortest id, then alphabetically.
 */
internal object ServiceArgumentMatching {
    private const val MAX_SERVICE_REPLACEMENT_QUICK_FIXES = 10

    fun serviceClass(
        project: Project,
        serviceId: String,
        localServiceClasses: Map<String, String>
    ): String? {
        return localServiceClasses[serviceId]
            ?.takeIf { it.isNotBlank() }
            ?: Services.className(project, serviceId)?.takeIf { it.isNotBlank() }
    }

    fun constructorParameterClasses(project: Project, parameter: Parameter): List<String> {
        return parameter.declaredType
            .global(project)
            .filterUnknown()
            .filterNull()
            .types
            .asSequence()
            .map { it.normalizeServiceClassFqn() }
            .filterNot { PhpType.isPrimitiveType(it) }
            .distinct()
            .toList()
    }

    fun isCompatible(project: Project, expectedClass: String, actualClass: String): Boolean {
        return PhpType.findSuper(
            expectedClass.normalizeServiceClassFqn(),
            actualClass.normalizeServiceClassFqn(),
            PhpIndex.getInstance(project)
        )
    }

    fun replacementServiceIds(
        project: Project,
        expectedClasses: List<String>,
        excludedServiceIds: Set<String>,
        localServiceClasses: Map<String, String>
    ): List<String> {
        return (localServiceClasses.keys + Services.allIds(project))
            .asSequence()
            .distinct()
            .filterNot { it in excludedServiceIds }
            .mapNotNull { serviceId ->
                // One index lookup plus a PhpType.findSuper per candidate — keep the sweep
                // over all services cancellable.
                ProgressManager.checkCanceled()
                serviceClass(project, serviceId, localServiceClasses)
                    ?.let { CandidateService(serviceId, it) }
            }
            .filter { candidate ->
                expectedClasses.any { expectedClass ->
                    isCompatible(project, expectedClass, candidate.classFqn)
                }
            }
            .sortedWith(
                compareBy(
                    { candidate -> if (expectedClasses.any { it.normalizeServiceClassFqn() == candidate.classFqn.normalizeServiceClassFqn() }) 0 else 1 },
                    { it.serviceId.length },
                    { it.serviceId }
                )
            )
            .map { it.serviceId }
            .take(MAX_SERVICE_REPLACEMENT_QUICK_FIXES)
            .toList()
    }

    /**
     * The single best service id for [expectedClasses] — the head of the [replacementServiceIds]
     * ranking — or `null` when no project service provides a compatible class.
     */
    fun bestServiceId(
        project: Project,
        expectedClasses: List<String>,
        currentServiceId: String,
        localServiceClasses: Map<String, String>
    ): String? {
        return replacementServiceIds(
            project = project,
            expectedClasses = expectedClasses,
            excludedServiceIds = setOf(currentServiceId),
            localServiceClasses = localServiceClasses
        ).firstOrNull()
    }

    fun localServiceClasses(yamlFile: YAMLFile?): Map<String, String> {
        val servicesMapping = ServicesYaml.servicesMapping(PsiTreeUtil.findChildOfType(yamlFile, YAMLMapping::class.java))
            ?: return emptyMap()

        return servicesMapping.keyValues
            .filter { ServicesYaml.isServiceDefinition(it) }
            .associate { it.keyText to ServicesYaml.serviceClass(it).orEmpty() }
    }

    private data class CandidateService(
        val serviceId: String,
        val classFqn: String,
    )
}
