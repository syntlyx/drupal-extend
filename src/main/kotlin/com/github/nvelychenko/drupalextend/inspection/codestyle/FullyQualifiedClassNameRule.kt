package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.PhpUseList

internal object FullyQualifiedClassNameRule : DrupalCodeStyleRule {
    override val id: String = "fully_qualified_class_name"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleFullyQualifiedClassNameEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        val classReference = element as? ClassReference ?: return
        classReference.fullyQualifiedClassNameImportCandidate() ?: return

        context.register(
            DrupalCodeStyleProblem(
                element = classReference,
                message = "Fully qualified class names must be imported with use statements",
                highlightType = ProblemHighlightType.WEAK_WARNING,
                rangeInElement = classReference.textRangeInSelf(),
                fixes = listOf(importClassReferenceFix())
            )
        )
    }

    private fun importClassReferenceFix(): DrupalCodeStyleTextEditQuickFix {
        return DrupalCodeStyleTextEditQuickFix("Import class and shorten reference") { _, descriptor ->
            val classReference = descriptor.psiElement as? ClassReference
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            classReference.containingFile.fullyQualifiedClassNameUseEdits(listOf(classReference))
        }
    }
}

internal fun PsiFile.fullyQualifiedClassNameUseEdits(): List<TextEdit> {
    val references = PsiTreeUtil.findChildrenOfType(this, ClassReference::class.java)
    return fullyQualifiedClassNameUseEdits(references)
}

private fun PsiFile.fullyQualifiedClassNameUseEdits(references: Collection<ClassReference>): List<TextEdit> {
    val candidates = references
        .mapNotNull { it.fullyQualifiedClassNameImportCandidate() }
        .safeImportCandidates()
    if (candidates.isEmpty()) return emptyList()

    val importEdit = missingClassImportsEdit(candidates.map { it.fqn })
    val replacementEdits = candidates.map {
        TextEdit(it.reference.textRange.startOffset, it.reference.textRange.endOffset, it.shortName)
    }

    return listOfNotNull(importEdit) + replacementEdits
}

private data class FullyQualifiedClassNameImportCandidate(
    val reference: ClassReference,
    val fqn: String,
    val shortName: String,
)

private fun ClassReference.fullyQualifiedClassNameImportCandidate(): FullyQualifiedClassNameImportCandidate? {
    if (PsiTreeUtil.getParentOfType(this, PhpUseList::class.java) != null) return null
    if (!text.trimStart().startsWith("\\")) return null

    val normalizedFqn = this.fqn?.trim('\\') ?: return null
    if (!normalizedFqn.contains("\\")) return null

    val namespace = normalizedFqn.substringBeforeLast("\\", missingDelimiterValue = "")
    if (namespace.isBlank()) return null
    // PHP namespaces are case-insensitive: compare without regard to case.
    if (namespace.equals(containingFile.currentPhpNamespace(), ignoreCase = true)) return null

    val shortName = normalizedFqn.substringAfterLast("\\")
    val existingImports = containingFile.classUseImports()
    // Reject if another import already occupies this short name (alias collision),
    // but allow if it is the same FQN (case-insensitive) — that import already covers this reference.
    if (existingImports.any {
            it.alias.equals(shortName, ignoreCase = true) &&
            !it.fqn.equals(normalizedFqn, ignoreCase = true)
        }) return null

    return FullyQualifiedClassNameImportCandidate(this, normalizedFqn, shortName)
}

private fun List<FullyQualifiedClassNameImportCandidate>.safeImportCandidates(): List<FullyQualifiedClassNameImportCandidate> {
    val fqnsByShortName = groupBy { it.shortName }
        .mapValues { (_, candidates) -> candidates.map { it.fqn }.distinct() }

    return filter { candidate ->
        fqnsByShortName[candidate.shortName]?.size == 1
    }
}

private data class ClassUseImport(
    val fqn: String,
    val alias: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    fun render(lineSeparator: String): String {
        val shortName = fqn.substringAfterLast("\\")
        val aliasSuffix = if (alias != shortName) " as $alias" else ""

        return "use $fqn$aliasSuffix;$lineSeparator"
    }
}

/**
 * Returns all top-level (file-scope) class use imports parsed via PSI.
 *
 * Using PSI instead of a text regex ensures that `use` statements inside
 * heredocs, nowdocs, or string literals are never mistakenly recognised as
 * real imports.
 */
private fun PsiFile.classUseImports(): List<ClassUseImport> {
    return PsiTreeUtil.findChildrenOfType(this, PhpUseList::class.java)
        .mapNotNull { it.psiClassUseImport() }
}

/**
 * Converts a PSI [PhpUseList] to a [ClassUseImport], or returns `null` if it
 * is not a simple top-level class import (e.g. trait import, function/const
 * import, grouped import, or a single-segment name with no namespace).
 */
private fun PhpUseList.psiClassUseImport(): ClassUseImport? {
    if (isTraitImport || isOfFunction || isOfConst) return null
    // Grouped or multi-import lists ("use A, B;" / "use A\{B, C};") are not handled.
    if (declarations.size != 1) return null
    val declaration = declarations[0]
    val fqn = declaration.targetReference?.fqn?.trim('\\') ?: return null
    // Must be a namespaced name (contains at least one backslash after trimming).
    if (!fqn.contains("\\")) return null
    // aliasName is the declared alias, or the simple class name when there is none.
    val alias = declaration.aliasName?.takeIf { it.isNotBlank() }
        ?: fqn.substringAfterLast("\\")
    // offsets are unused in text-edit generation; pass -1 as sentinel.
    return ClassUseImport(fqn, alias, -1, -1)
}

/**
 * Returns PSI-based descriptors for all top-level use statements (class, function,
 * and const) so that offset-based text edits can be generated correctly.
 *
 * Using PSI guarantees that heredoc/nowdoc content that textually resembles a
 * `use` statement never corrupts the rule's view of imports.
 */
private fun PsiFile.topLevelUseStatements(): List<UseStatementMatch> {
    val fileText = text
    return PsiTreeUtil.findChildrenOfType(this, PhpUseList::class.java)
        .filter { !it.isTraitImport }
        .map { useList ->
            val psiStart = useList.textRange.startOffset
            // Extend the end offset to swallow trailing horizontal whitespace
            // and one optional line-terminator, matching the original regex:
            //   [ \t]*(?:\r?\n)?
            var psiEnd = useList.textRange.endOffset
            while (psiEnd < fileText.length && (fileText[psiEnd] == ' ' || fileText[psiEnd] == '\t')) {
                psiEnd++
            }
            if (psiEnd < fileText.length && fileText[psiEnd] == '\r') psiEnd++
            if (psiEnd < fileText.length && fileText[psiEnd] == '\n') psiEnd++
            UseStatementMatch(
                body = useList.text.removePrefix("use").removeSuffix(";").trim(),
                startOffset = psiStart,
                endOffset = psiEnd,
            )
        }
        .sortedBy { it.startOffset }
}

private data class UseStatementMatch(
    val body: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    fun classUseImport(): ClassUseImport? {
        if (body.startsWith("function ", ignoreCase = true)) return null
        if (body.startsWith("const ", ignoreCase = true)) return null
        if (body.contains("{") || body.contains(",")) return null

        val parts = body.split(Regex("""\s+as\s+""", RegexOption.IGNORE_CASE), limit = 2)
        val fqn = parts[0].trim().trim('\\')
        if (!fqn.contains("\\")) return null

        val alias = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: fqn.substringAfterLast("\\")
        return ClassUseImport(fqn, alias, startOffset, endOffset)
    }
}

private fun PsiFile.missingClassImportsEdit(fqns: Collection<String>): TextEdit? {
    val document = document() ?: return null
    val existingImports = classUseImports()
    // Use case-insensitive lookup: PHP FQNs are case-insensitive.
    val existingFqnsLower = existingImports.mapTo(mutableSetOf()) { it.fqn.lowercase() }
    val missingImports = fqns
        .distinct()
        .filterNot { it.lowercase() in existingFqnsLower }
        .map { ClassUseImport(it, it.substringAfterLast("\\"), -1, -1) }
    if (missingImports.isEmpty()) return null

    val lineSeparator = document.lineSeparator()
    val topLevelUses = topLevelUseStatements()
    if (topLevelUses.isNotEmpty() && topLevelUses.size == existingImports.size) {
        val sortedImports = (existingImports + missingImports)
            .distinctBy { it.fqn.lowercase() to it.alias.lowercase() }
            .sortedWith(compareBy<ClassUseImport> { it.fqn.lowercase() }.thenBy { it.alias.lowercase() })

        return TextEdit(
            topLevelUses.first().startOffset,
            topLevelUses.last().endOffset,
            sortedImports.joinToString(separator = "") { it.render(lineSeparator) }
        )
    }

    val insertionOffset = topLevelUses.lastOrNull()?.endOffset
        ?: namespaceLineEndOffset()
        ?: phpOpenTagLineEndOffset()
        ?: 0
    val prefix = if (topLevelUses.isEmpty()) lineSeparator else ""
    val suffix = if (text.getOrNull(insertionOffset).isLineBreak()) "" else lineSeparator
    val importsText = missingImports
        .sortedWith(compareBy<ClassUseImport> { it.fqn.lowercase() }.thenBy { it.alias.lowercase() })
        .joinToString(separator = "") { it.render(lineSeparator) }

    return TextEdit(insertionOffset, prefix + importsText + suffix)
}

private fun PsiFile.currentPhpNamespace(): String {
    return PHP_NAMESPACE.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim('\\')
        .orEmpty()
}

private fun PsiFile.namespaceLineEndOffset(): Int? {
    return PHP_NAMESPACE_LINE.find(text)
        ?.range
        ?.last
        ?.plus(1)
}

private fun PsiFile.phpOpenTagLineEndOffset(): Int? {
    return PHP_OPEN_TAG_LINE.find(text)
        ?.range
        ?.last
        ?.plus(1)
}

private fun PsiElement.textRangeInSelf() = com.intellij.openapi.util.TextRange(0, textLength)

private fun Char?.isLineBreak(): Boolean {
    return this == '\n' || this == '\r'
}

private val PHP_NAMESPACE = Regex("""(?m)^namespace\s+([^;\r\n]+);""")
private val PHP_NAMESPACE_LINE = Regex("""(?m)^namespace[ \t]+[^;\r\n]+;[ \t]*(?:\r?\n)?""")
private val PHP_OPEN_TAG_LINE = Regex("""(?m)^<\?php[ \t]*(?:\r?\n)?""")
