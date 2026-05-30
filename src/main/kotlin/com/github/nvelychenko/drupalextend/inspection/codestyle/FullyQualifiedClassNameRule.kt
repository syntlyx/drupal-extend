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
    if (namespace == containingFile.currentPhpNamespace()) return null

    val shortName = normalizedFqn.substringAfterLast("\\")
    val existingImports = containingFile.classUseImports()
    if (existingImports.any { it.alias == shortName && it.fqn != normalizedFqn }) return null

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

private fun PsiFile.classUseImports(): List<ClassUseImport> {
    return topLevelUseStatements()
        .mapNotNull { it.classUseImport() }
}

private fun PsiFile.topLevelUseStatements(): List<UseStatementMatch> {
    return TOP_LEVEL_USE_STATEMENT.findAll(text)
        .map { match ->
            UseStatementMatch(
                body = match.groupValues[1].trim(),
                startOffset = match.range.first,
                endOffset = match.range.last + 1,
            )
        }
        .toList()
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
    val existingFqns = existingImports.mapTo(mutableSetOf()) { it.fqn }
    val missingImports = fqns
        .distinct()
        .filterNot { it in existingFqns }
        .map { ClassUseImport(it, it.substringAfterLast("\\"), -1, -1) }
    if (missingImports.isEmpty()) return null

    val lineSeparator = document.lineSeparator()
    val topLevelUses = topLevelUseStatements()
    if (topLevelUses.isNotEmpty() && topLevelUses.size == existingImports.size) {
        val sortedImports = (existingImports + missingImports)
            .distinctBy { it.fqn to it.alias }
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

private val TOP_LEVEL_USE_STATEMENT = Regex("""(?m)^use\s+([^;]+);[ \t]*(?:\r?\n)?""")
private val PHP_NAMESPACE = Regex("""(?m)^namespace\s+([^;\r\n]+);""")
private val PHP_NAMESPACE_LINE = Regex("""(?m)^namespace[ \t]+[^;\r\n]+;[ \t]*(?:\r?\n)?""")
private val PHP_OPEN_TAG_LINE = Regex("""(?m)^<\?php[ \t]*(?:\r?\n)?""")
