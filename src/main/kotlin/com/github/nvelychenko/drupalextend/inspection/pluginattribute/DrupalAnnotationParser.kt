package com.github.nvelychenko.drupalextend.inspection.pluginattribute

import com.intellij.openapi.util.TextRange

internal data class DrupalAnnotationBlock(
    val annotation: DrupalAnnotation,
    val removalRange: TextRange,
)

internal data class DrupalAnnotation(
    val name: String,
    val arguments: List<DrupalAnnotationArgument>,
)

internal data class DrupalAnnotationArgument(
    val name: String?,
    val value: DrupalAnnotationValue,
)

internal data class DrupalArrayEntry(
    val key: DrupalAnnotationValue?,
    val value: DrupalAnnotationValue,
)

internal sealed interface DrupalAnnotationValue {
    data class StringLiteral(val value: String) : DrupalAnnotationValue
    data class NumberLiteral(val value: String) : DrupalAnnotationValue
    data class BooleanLiteral(val value: Boolean) : DrupalAnnotationValue
    data object NullLiteral : DrupalAnnotationValue
    data class ConstantLiteral(val value: String) : DrupalAnnotationValue
    data class ArrayLiteral(val entries: List<DrupalArrayEntry>) : DrupalAnnotationValue
    data class AnnotationLiteral(val annotation: DrupalAnnotation) : DrupalAnnotationValue
}

internal object DrupalAnnotationParser {
    fun parse(docText: String, spec: DrupalPluginAttributeSpec): DrupalAnnotationBlock? {
        val block = findAnnotationBlock(docText, spec.annotationName) ?: return null
        val rawAnnotation = docText.substring(block.annotationStart, block.annotationEnd)
        val cleanAnnotation = rawAnnotation.lines()
            .joinToString("\n") { it.replace(Regex("""^\s*\*\s?"""), "") }
        val annotation = AnnotationSyntaxParser(cleanAnnotation).parseAnnotation() ?: return null
        if (annotation.name != spec.annotationName) return null
        val removalRange = if (docText.hasMeaningfulDocTextAfterRemoving(block.removalRange)) {
            block.removalRange
        } else {
            TextRange(0, docText.length)
        }

        return DrupalAnnotationBlock(annotation, removalRange)
    }

    private fun String.hasMeaningfulDocTextAfterRemoving(removalRange: TextRange): Boolean {
        val remainingDocText = replaceRange(removalRange.startOffset, removalRange.endOffset, "")

        return remainingDocText.lines()
            .map { line ->
                line
                    .replace("/**", "")
                    .replace("*/", "")
                    .replace(Regex("""^\s*\*\s?"""), "")
                    .trim()
            }
            .any { it.isNotBlank() }
    }

    private fun findAnnotationBlock(docText: String, annotationName: String): RawAnnotationBlock? {
        val match = Regex("""@${Regex.escape(annotationName)}\s*\(""")
            .find(docText)
            ?: return null
        val annotationStart = match.range.first
        val openParen = docText.indexOf('(', annotationStart)
        if (openParen < 0) return null

        val closeParen = docText.findMatchingParen(openParen) ?: return null
        var removalStart = docText.lastIndexOf('\n', annotationStart).let { if (it < 0) 0 else it + 1 }
        val previousLineEnd = (removalStart - 1).takeIf { it >= 0 }
        val previousLineStart = previousLineEnd?.let {
            docText.lastIndexOf('\n', it - 1).let { index -> if (index < 0) 0 else index + 1 }
        }
        if (previousLineStart != null) {
            val previousLine = docText.substring(previousLineStart, previousLineEnd).trim()
            if (previousLine == "*") {
                removalStart = previousLineStart
            }
        }

        val removalEnd = docText.indexOf('\n', closeParen).let { if (it < 0) docText.length else it + 1 }

        return RawAnnotationBlock(
            annotationStart = annotationStart,
            annotationEnd = closeParen + 1,
            removalRange = TextRange(removalStart, removalEnd)
        )
    }

    private fun String.findMatchingParen(openParenOffset: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escaped = false

        for (index in openParenOffset until length) {
            val char = this[index]
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                continue
            }

            when (char) {
                '\'', '"' -> quote = char
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }

        return null
    }

    private data class RawAnnotationBlock(
        val annotationStart: Int,
        val annotationEnd: Int,
        val removalRange: TextRange,
    )
}

private class AnnotationSyntaxParser(
    private val text: String,
) {
    private var index = 0

    fun parseAnnotation(): DrupalAnnotation? {
        skipWhitespace()
        if (text.getOrNull(index) != '@') return null
        index++
        val name = readName() ?: return null
        skipWhitespace()
        val arguments = if (text.getOrNull(index) == '(') {
            index++
            parseArguments(')') ?: return null
        } else {
            emptyList()
        }

        return DrupalAnnotation(name, arguments)
    }

    private fun parseArguments(closingChar: Char): List<DrupalAnnotationArgument>? {
        val arguments = mutableListOf<DrupalAnnotationArgument>()

        while (index < text.length) {
            skipWhitespaceAndCommas()
            if (text.getOrNull(index) == closingChar) {
                index++
                return arguments
            }

            val checkpoint = index
            val name = readName()
            if (name != null) {
                skipWhitespace()
                if (text.getOrNull(index) == '=') {
                    index++
                    val value = parseValue() ?: return null
                    arguments += DrupalAnnotationArgument(name, value)
                    continue
                }
            }

            index = checkpoint
            val value = parseValue() ?: return null
            arguments += DrupalAnnotationArgument(null, value)
        }

        return null
    }

    private fun parseArray(): DrupalAnnotationValue.ArrayLiteral? {
        if (text.getOrNull(index) != '{') return null
        index++
        val entries = mutableListOf<DrupalArrayEntry>()

        while (index < text.length) {
            skipWhitespaceAndCommas()
            if (text.getOrNull(index) == '}') {
                index++
                return DrupalAnnotationValue.ArrayLiteral(entries)
            }

            val checkpoint = index
            val keyCandidate = parseValue() ?: return null
            skipWhitespace()
            if (text.getOrNull(index) == '=') {
                index++
                val value = parseValue() ?: return null
                entries += DrupalArrayEntry(keyCandidate, value)
            } else {
                index = checkpoint
                val value = parseValue() ?: return null
                entries += DrupalArrayEntry(null, value)
            }
        }

        return null
    }

    private fun parseValue(): DrupalAnnotationValue? {
        skipWhitespace()

        return when (val char = text.getOrNull(index)) {
            '@' -> parseAnnotation()?.let { DrupalAnnotationValue.AnnotationLiteral(it) }
            '{' -> parseArray()
            '\'', '"' -> parseString()
            null -> null
            else -> {
                if (char == '-' || char.isDigit()) {
                    parseNumberOrRawLiteral()
                } else {
                    parseRawLiteral()
                }
            }
        }
    }

    private fun parseString(): DrupalAnnotationValue.StringLiteral? {
        val quote = text.getOrNull(index) ?: return null
        if (quote != '\'' && quote != '"') return null
        index++

        val value = StringBuilder()
        var escaped = false
        while (index < text.length) {
            val char = text[index++]
            when {
                escaped -> {
                    value.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == quote -> return DrupalAnnotationValue.StringLiteral(value.toString())
                else -> value.append(char)
            }
        }

        return null
    }

    private fun parseNumberOrRawLiteral(): DrupalAnnotationValue {
        val start = index
        if (text.getOrNull(index) == '-') index++
        while (text.getOrNull(index)?.isDigit() == true) index++
        if (text.getOrNull(index) == '.') {
            index++
            while (text.getOrNull(index)?.isDigit() == true) index++
        }

        val number = text.substring(start, index)
        if (number.matches(Regex("""-?\d+(?:\.\d+)?"""))) {
            return DrupalAnnotationValue.NumberLiteral(number)
        }

        index = start
        return parseRawLiteral()
    }

    private fun parseRawLiteral(): DrupalAnnotationValue {
        val start = index
        while (index < text.length && !text[index].isRawLiteralDelimiter()) index++
        val raw = text.substring(start, index).trim()

        return when (raw.lowercase()) {
            "true" -> DrupalAnnotationValue.BooleanLiteral(true)
            "false" -> DrupalAnnotationValue.BooleanLiteral(false)
            "null" -> DrupalAnnotationValue.NullLiteral
            else -> DrupalAnnotationValue.ConstantLiteral(raw)
        }
    }

    private fun readName(): String? {
        skipWhitespace()
        val start = index
        if (text.getOrNull(index)?.let { it.isLetter() || it == '_' } != true) return null
        index++
        while (text.getOrNull(index)?.let { it.isLetterOrDigit() || it == '_' } == true) index++

        return text.substring(start, index)
    }

    private fun skipWhitespaceAndCommas() {
        while (index < text.length && (text[index].isWhitespace() || text[index] == ',')) index++
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun Char.isRawLiteralDelimiter(): Boolean {
        return isWhitespace() || this == ',' || this == ')' || this == '}' || this == '='
    }
}
