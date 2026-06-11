package com.github.nvelychenko.drupalextend.completion.providers

import com.github.nvelychenko.drupalextend.util.ServiceTags
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import org.jetbrains.yaml.psi.YAMLScalar

class ServicesYamlCompletionProvider : CompletionProvider<CompletionParameters>() {
    public override fun addCompletions(
        completionParameters: CompletionParameters,
        processingContext: ProcessingContext,
        completionResultSet: CompletionResultSet
    ) {
        val project = completionParameters.position.project

        if (!Services.isSupportEnabled(project)) return
        // The original file carries the real VirtualFile; the completion copy can have a null/light
        // one, so the *.services.yml gate must read the original.
        if (!ServicesYaml.isServicesFileName(completionParameters.originalFile.virtualFile?.name)) return

        // `position` is the dummy-inserted leaf in the completion copy — its scalar is present even
        // at an empty value (`name: <caret>`, `!tagged_iterator <caret>`), unlike `originalPosition`.
        // The service paths below only act on already-typed prefixes, so they keep the
        // original-position behaviour; tag positions can be empty and need the copy leaf.
        val copyScalar = PsiTreeUtil.getParentOfType(completionParameters.position, YAMLScalar::class.java, false)
        if (copyScalar != null
            && !ServicesYaml.isKey(copyScalar)
            && copyScalar.isTagNamePosition()
        ) {
            addTagNameCompletions(project, completionResultSet)
            completionResultSet.stopHere()
            return
        }

        val position = completionParameters.originalPosition ?: return

        val scalar = PsiTreeUtil.getParentOfType(position, YAMLScalar::class.java, false) ?: return
        if (!ServicesYaml.isServicesFileName(scalar.containingFile.virtualFile?.name)) return
        if (ServicesYaml.isKey(scalar)) return

        val completionMode = scalar.serviceCompletionMode() ?: return
        when (completionMode) {
            ServiceCompletionMode.CLASS -> addClassCompletions(scalar, completionResultSet)
            ServiceCompletionMode.PREFIXED,
            ServiceCompletionMode.PLAIN -> addServiceCompletions(project, completionMode, completionResultSet)
        }

        completionResultSet.stopHere()
    }

    private fun addServiceCompletions(
        project: Project,
        completionMode: ServiceCompletionMode,
        completionResultSet: CompletionResultSet
    ) {
        Services.allIds(project)
            .forEach { serviceId ->
                val serviceClass = Services.className(project, serviceId)
                var lookupElement = LookupElementBuilder.create(completionMode.lookupString(serviceId))

                if (completionMode == ServiceCompletionMode.PREFIXED) {
                    lookupElement = lookupElement.withInsertHandler(ServiceReferenceInsertHandler.INSTANCE)
                }

                if (!serviceClass.isNullOrBlank()) {
                    lookupElement = lookupElement.withTypeText(serviceClass, true)
                }

                completionResultSet.addElement(lookupElement)
            }
    }

    private fun YAMLScalar.isTagNamePosition(): Boolean {
        return ServicesYaml.isTagNameValuePosition(this)
                || ServicesYaml.taggedArgumentTagName(this) != null
    }

    private fun addTagNameCompletions(
        project: Project,
        completionResultSet: CompletionResultSet
    ) {
        // The caret may sit in an empty value (`name: <caret>`) where the auto-inserted dummy
        // identifier would otherwise filter every tag out; match against an empty prefix and let
        // the platform re-filter on the real characters typed.
        val tagResultSet = completionResultSet.withPrefixMatcher("")
        ServiceTags.allTagNames(project).forEach { tagName ->
            tagResultSet.addElement(
                LookupElementBuilder.create(tagName).withTypeText("service tag", true)
            )
        }
    }

    private fun addClassCompletions(
        scalar: YAMLScalar,
        completionResultSet: CompletionResultSet
    ) {
        val prefix = scalar.classCompletionPrefix()
        val classResultSet = completionResultSet.withPrefixMatcher("")

        val phpIndex = PhpIndex.getInstance(scalar.project)

        sequenceOf(
            phpIndex.getAllClassFqns(null as PrefixMatcher?)
                .asSequence()
                .map { ClassCompletionCandidate(it, "class") },
            phpIndex.getAllInterfacesFqns(null as PrefixMatcher?)
                .asSequence()
                .map { ClassCompletionCandidate(it, "interface") }
        )
            .flatten()
            .map { it.copy(fqn = it.fqn.removePrefix("\\")) }
            .filter { it.fqn.isNotBlank() }
            .distinctBy { it.fqn }
            .filter { it.fqn.matchesClassPrefix(prefix) }
            .sortedWith(classCompletionComparator(prefix))
            .forEach { candidate ->
                classResultSet.addElement(candidate.toClassLookupElement(prefix))
            }
    }

    private fun YAMLScalar.serviceCompletionMode(): ServiceCompletionMode? {
        val currentValue = textValue.trim()
        val keyPath = ServicesYaml.serviceKeyPath(this) ?: return null

        if (!keyPath.startsWith("services.")) return null

        if (currentValue.startsWith("@") && !currentValue.startsWith("@@")) {
            return ServiceCompletionMode.PREFIXED
        }

        if (ServicesYaml.isClassReferenceKeyPath(keyPath)) {
            return ServiceCompletionMode.CLASS
        }

        if (ServicesYaml.isServiceReferenceKeyPath(keyPath)) {
            return ServiceCompletionMode.PREFIXED
        }

        if (ServicesYaml.isPlainServiceIdKeyPath(keyPath)) {
            return ServiceCompletionMode.PLAIN
        }

        return null
    }

    private enum class ServiceCompletionMode {
        CLASS,
        PREFIXED,
        PLAIN;

        fun lookupString(serviceId: String): String {
            return when (this) {
                CLASS -> serviceId
                PREFIXED -> "@$serviceId"
                PLAIN -> serviceId
            }
        }
    }

    private fun YAMLScalar.classCompletionPrefix(): String {
        return textValue
            .trim()
            .substringBefore("::")
            .removePrefix("\\")
    }

    private fun String.matchesClassPrefix(prefix: String): Boolean {
        if (prefix.isBlank()) return true

        return startsWith(prefix, ignoreCase = true)
                || substringAfterLast("\\").startsWith(prefix, ignoreCase = true)
    }

    private data class ClassCompletionCandidate(
        val fqn: String,
        val kind: String,
    )

    private fun classCompletionComparator(prefix: String): Comparator<ClassCompletionCandidate> {
        return compareBy<ClassCompletionCandidate> { it.relevance(prefix) }
            .thenBy { it.fqn.length }
            .thenBy { it.fqn }
    }

    private fun ClassCompletionCandidate.relevance(prefix: String): Int {
        if (prefix.isBlank()) return 0

        val lowerPrefix = prefix.lowercase()
        val lowerFqn = fqn.lowercase()
        val lowerShortName = fqn.substringAfterLast("\\").lowercase()

        return when {
            lowerFqn.startsWith(lowerPrefix) -> 0
            lowerShortName.startsWith(lowerPrefix) -> 1
            lowerFqn.contains("\\$lowerPrefix") -> 2
            else -> 3
        }
    }

    private fun ClassCompletionCandidate.priority(prefix: String): Double {
        return 10_000.0 - (relevance(prefix) * 1_000.0) - fqn.length
    }

    private fun ClassCompletionCandidate.toClassLookupElement(prefix: String): LookupElement {
        val namespace = fqn.substringBeforeLast("\\", "")
        val shortName = fqn.substringAfterLast("\\")

        val lookupElement = LookupElementBuilder.create(fqn)
            .withPresentableText(shortName)
            .withTailText(namespace.takeIf { it.isNotBlank() }?.let { " $it" }, true)
            .withTypeText(kind, true)
            .withInsertHandler(ServiceClassInsertHandler.INSTANCE)

        return PrioritizedLookupElement.withPriority(lookupElement, priority(prefix))
    }

    private class ServiceReferenceInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            if (!item.lookupString.startsWith("@")) return
            if (context.startOffset <= 0) return

            val document = context.document
            if (document.charsSequence[context.startOffset - 1] == '@') {
                document.deleteString(context.startOffset - 1, context.startOffset)
            }
        }

        companion object {
            val INSTANCE = ServiceReferenceInsertHandler()
        }
    }

    private class ServiceClassInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val document = context.document
            val prefixEndOffset = context.startOffset
            val prefixStartOffset = findClassPrefixStartOffset(document.charsSequence, prefixEndOffset)

            if (prefixStartOffset >= prefixEndOffset) return

            document.deleteString(prefixStartOffset, prefixEndOffset)
            context.tailOffset -= (prefixEndOffset - prefixStartOffset)
        }

        private fun findClassPrefixStartOffset(chars: CharSequence, offset: Int): Int {
            var startOffset = offset
            while (startOffset > 0 && chars[startOffset - 1].isClassNameChar()) {
                startOffset--
            }

            return startOffset
        }

        private fun Char.isClassNameChar(): Boolean {
            return isLetterOrDigit() || this == '_' || this == '\\'
        }

        companion object {
            val INSTANCE = ServiceClassInsertHandler()
        }
    }
}
