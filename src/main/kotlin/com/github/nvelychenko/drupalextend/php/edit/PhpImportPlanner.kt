package com.github.nvelychenko.drupalextend.php.edit

import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.jetbrains.php.lang.psi.elements.PhpClass

internal data class PhpImportPlanner(
    private val fileText: String,
    private val phpClass: PhpClass,
) {
    private val importsToAdd = linkedSetOf<String>()
    private val classStartOffset = phpClass.textRange.startOffset
    private val filePrefix = fileText.take(classStartOffset)
    private val importBlock = PhpImportBlock.parse(filePrefix)
    private val existingImports = importBlock.classImports

    fun typeName(classFqn: String): String {
        val normalizedFqn = classFqn.normalizeServiceClassFqn().removePrefix("\\")
        val shortName = normalizedFqn.substringAfterLast("\\")
        val namespace = normalizedFqn.substringBeforeLast("\\", missingDelimiterValue = "")
        val currentNamespace = phpClass.namespaceName.trim('\\')
        if (namespace == currentNamespace) return shortName

        existingImports.firstOrNull { it.fqn == normalizedFqn }?.let { return it.alias }
        if (existingImports.any { it.alias == shortName && it.fqn != normalizedFqn }) {
            return "\\$normalizedFqn"
        }

        importsToAdd += normalizedFqn
        return shortName
    }

    fun importsInsertionEdit(): TextEdit? {
        val missingImports = importsToAdd
            .filterNot { importFqn -> existingImports.any { it.fqn == importFqn } }
            .sorted()
        if (missingImports.isEmpty()) return null

        val insertionOffset = importBlock.lastUseLineEndOffset()
            ?: namespaceLineEndOffset()
            ?: phpOpenTagLineEndOffset()
            ?: 0
        val prefix = if (importBlock.lastUseLineEndOffset() == null) "\n" else ""
        val suffix = if (fileText.getOrNull(insertionOffset).isLineBreak()) "" else "\n"
        val importsText = missingImports.joinToString(separator = "") { "use $it;\n" }

        return TextEdit(insertionOffset, prefix + importsText + suffix)
    }

    fun sortedImportsEdit(): TextEdit? {
        return importBlock.sortedClassImportsEdit()
    }

    private fun namespaceLineEndOffset(): Int? {
        return Regex("""(?m)^namespace[ \t]+[^;\r\n]+;[ \t]*(?:\r?\n)?""")
            .find(filePrefix)
            ?.range
            ?.last
            ?.plus(1)
    }

    private fun phpOpenTagLineEndOffset(): Int? {
        return Regex("""(?m)^<\?php[ \t]*(?:\r?\n)?""")
            .find(filePrefix)
            ?.range
            ?.last
            ?.plus(1)
    }

    private fun Char?.isLineBreak(): Boolean {
        return this == '\n' || this == '\r'
    }
}

private data class PhpImportBlock(
    private val allUseStatements: List<UseStatement>,
    val classImports: List<ClassImportStatement>,
) {
    fun lastUseLineEndOffset(): Int? {
        return allUseStatements.lastOrNull()?.endOffset
    }

    fun sortedClassImportsEdit(): TextEdit? {
        if (classImports.size < 2) return null
        if (allUseStatements.size != classImports.size) return null

        val sortedImports = classImports.sortedWith(
            compareBy<ClassImportStatement> { it.fqn.lowercase() }
                .thenBy { it.alias.lowercase() }
        )
        if (sortedImports.map { it.render() } == classImports.map { it.render() }) return null

        return TextEdit(
            classImports.first().startOffset,
            classImports.last().endOffset,
            sortedImports.joinToString(separator = "") { it.render() }
        )
    }

    companion object {
        private val ALL_USE_STATEMENT_REGEX = Regex("""(?m)^use\s+([^;]+);[ \t]*\n?""")

        fun parse(filePrefix: String): PhpImportBlock {
            val allUseStatements = ALL_USE_STATEMENT_REGEX.findAll(filePrefix)
                .map { match ->
                    UseStatement(
                        body = match.groupValues[1].trim(),
                        startOffset = match.range.first,
                        endOffset = match.range.last + 1,
                    )
                }
                .toList()

            val classImports = allUseStatements
                .mapNotNull { it.classImport() }
                .toList()

            return PhpImportBlock(allUseStatements, classImports)
        }
    }
}

private data class UseStatement(
    val body: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    fun classImport(): ClassImportStatement? {
        if (body.startsWith("function ", ignoreCase = true)) return null
        if (body.startsWith("const ", ignoreCase = true)) return null
        if (body.contains("{") || body.contains(",")) return null

        val parts = body.split(Regex("""\s+as\s+""", RegexOption.IGNORE_CASE), limit = 2)
        val fqn = parts[0].trim().trim('\\')
        if (fqn.isBlank()) return null

        val alias = parts.getOrNull(1)?.trim() ?: fqn.substringAfterLast("\\")
        return ClassImportStatement(fqn, alias, startOffset, endOffset)
    }
}

private data class ClassImportStatement(
    val fqn: String,
    val alias: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    fun render(): String {
        val shortName = fqn.substringAfterLast("\\")
        val aliasSuffix = if (alias != shortName) " as $alias" else ""

        return "use $fqn$aliasSuffix;\n"
    }
}
