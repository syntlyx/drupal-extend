package com.github.nvelychenko.drupalextend.inspection.pluginattribute

import com.github.nvelychenko.drupalextend.php.edit.PhpImportPlanner
import com.github.nvelychenko.drupalextend.php.edit.escapePhpSingleQuoted
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

private const val TRANSLATABLE_MARKUP_FQN = "\\Drupal\\Core\\StringTranslation\\TranslatableMarkup"

internal data class DrupalPluginAttributeConversion(
    val spec: DrupalPluginAttributeSpec,
    val removalRange: TextRange,
    private val arguments: List<DrupalAnnotationArgument>,
) {
    fun attributeText(imports: PhpImportPlanner): String? {
        val attributeType = imports.typeName(spec.attributeFqn)
        val renderedArguments = arguments.map { argument ->
            argument.render(imports, indentLevel = 1) ?: return null
        }

        if (renderedArguments.isEmpty()) {
            return "#[$attributeType]"
        }

        return "#[$attributeType(\n${renderedArguments.joinToString("\n")}\n)]"
    }
}

internal object DrupalPluginAttributeConverter {
    fun findFirst(docText: String, project: Project): DrupalPluginAttributeConversion? {
        return DrupalPluginAttributeRegistry.supportedSpecs(project)
            .firstNotNullOfOrNull { spec -> parse(docText, spec) }
    }

    fun parse(docText: String, annotationName: String, project: Project): DrupalPluginAttributeConversion? {
        val spec = DrupalPluginAttributeRegistry.find(annotationName, project) ?: return null
        return parse(docText, spec)
    }

    private fun parse(docText: String, spec: DrupalPluginAttributeSpec): DrupalPluginAttributeConversion? {
        val block = DrupalAnnotationParser.parse(docText, spec) ?: return null
        if (!spec.supportsArguments(block.annotation.arguments)) return null
        if (!block.annotation.canRender()) return null

        return DrupalPluginAttributeConversion(
            spec = spec,
            removalRange = block.removalRange,
            arguments = block.annotation.arguments,
        )
    }
}

private fun DrupalPluginAttributeSpec.supportsArguments(arguments: List<DrupalAnnotationArgument>): Boolean {
    val supportedArguments = this.supportedArguments ?: return true

    return arguments.all { argument -> argument.name == null || argument.name in supportedArguments }
}

private fun DrupalAnnotation.canRender(): Boolean {
    return arguments.all { it.value.canRender() }
}

private fun DrupalAnnotationValue.canRender(): Boolean {
    return when (this) {
        is DrupalAnnotationValue.AnnotationLiteral -> when (annotation.name) {
            "Translation", "PluralTranslation" -> annotation.arguments.all { it.value.canRender() }
            else -> DrupalNestedAnnotationRegistry.findClassFqn(annotation.name) != null && annotation.canRender()
        }
        is DrupalAnnotationValue.ArrayLiteral -> entries.all { entry ->
            entry.key?.canRender() != false && entry.value.canRender()
        }
        is DrupalAnnotationValue.ConstantLiteral -> value.isNotBlank()
        else -> true
    }
}

private fun DrupalAnnotationArgument.render(imports: PhpImportPlanner, indentLevel: Int): String? {
    val renderedValue = value.render(imports, indentLevel) ?: return null
    val prefix = name?.let { "$it: " }.orEmpty()

    return "${indent(indentLevel)}$prefix$renderedValue,"
}

private fun DrupalAnnotationValue.render(imports: PhpImportPlanner, indentLevel: Int): String? {
    return when (this) {
        is DrupalAnnotationValue.StringLiteral -> "'${value.escapePhpSingleQuoted()}'"
        is DrupalAnnotationValue.NumberLiteral -> value
        is DrupalAnnotationValue.BooleanLiteral -> value.toString()
        DrupalAnnotationValue.NullLiteral -> "null"
        is DrupalAnnotationValue.ConstantLiteral -> value
        is DrupalAnnotationValue.ArrayLiteral -> renderArray(imports, indentLevel)
        is DrupalAnnotationValue.AnnotationLiteral -> annotation.renderNestedAnnotation(imports, indentLevel)
    }
}

private fun DrupalAnnotationValue.ArrayLiteral.renderArray(imports: PhpImportPlanner, indentLevel: Int): String? {
    if (entries.isEmpty()) return "[]"
    if (entries.all { it.isSimpleArrayEntry() }) {
        val renderedEntries = entries.map { entry ->
            val value = entry.value.render(imports, indentLevel) ?: return null
            val key = entry.key?.let { it.render(imports, indentLevel) ?: return null }

            if (key == null) value else "$key => $value"
        }

        return renderedEntries.joinToString(prefix = "[", postfix = "]")
    }

    val renderedEntries = entries.map { entry ->
        val value = entry.value.render(imports, indentLevel + 1) ?: return null
        val key = entry.key?.let { it.render(imports, indentLevel + 1) ?: return null }
        val body = if (key == null) value else "$key => $value"

        "${indent(indentLevel + 1)}$body,"
    }

    return "[\n${renderedEntries.joinToString("\n")}\n${indent(indentLevel)}]"
}

private fun DrupalArrayEntry.isSimpleArrayEntry(): Boolean {
    return key.isSimpleValue() && value.isSimpleValue()
}

private fun DrupalAnnotationValue?.isSimpleValue(): Boolean {
    return when (this) {
        null -> true
        is DrupalAnnotationValue.ArrayLiteral -> entries.all { it.isSimpleArrayEntry() }
        is DrupalAnnotationValue.AnnotationLiteral -> false
        else -> true
    }
}

private fun DrupalAnnotation.renderNestedAnnotation(imports: PhpImportPlanner, indentLevel: Int): String? {
    return when (name) {
        "Translation" -> renderTranslation(imports)
        "PluralTranslation" -> renderPluralTranslation(imports, indentLevel)
        else -> renderObjectValue(imports, indentLevel)
    }
}

private fun DrupalAnnotation.renderTranslation(imports: PhpImportPlanner): String? {
    val translation = arguments.firstOrNull()?.value as? DrupalAnnotationValue.StringLiteral ?: return null
    if (arguments.drop(1).any { it.name != "context" }) return null
    val translatableMarkupType = imports.typeName(TRANSLATABLE_MARKUP_FQN)
    val context = arguments.firstOrNull { it.name == "context" }?.value as? DrupalAnnotationValue.StringLiteral

    return if (context == null) {
        "new $translatableMarkupType('${translation.value.escapePhpSingleQuoted()}')"
    } else {
        "new $translatableMarkupType('${translation.value.escapePhpSingleQuoted()}', [], ['context' => '${context.value.escapePhpSingleQuoted()}'])"
    }
}

private fun DrupalAnnotation.renderPluralTranslation(imports: PhpImportPlanner, indentLevel: Int): String? {
    val entries = arguments.map { argument ->
        val name = argument.name ?: return null
        DrupalArrayEntry(
            key = DrupalAnnotationValue.StringLiteral(name),
            value = argument.value
        )
    }

    return DrupalAnnotationValue.ArrayLiteral(entries).renderArray(imports, indentLevel)
}

private fun DrupalAnnotation.renderObjectValue(imports: PhpImportPlanner, indentLevel: Int): String? {
    val classFqn = DrupalNestedAnnotationRegistry.findClassFqn(name) ?: return null
    val className = imports.typeName(classFqn)
    if (arguments.isEmpty()) return "new $className()"

    val renderedArguments = arguments.map { argument ->
        val renderedValue = argument.value.render(imports, indentLevel + 1) ?: return null
        val argumentName = argument.name?.let { if (name == "ContextDefinition" && it == "value") "data_type" else it }
        val prefix = argumentName?.let { "$it: " }.orEmpty()

        "${indent(indentLevel + 1)}$prefix$renderedValue,"
    }

    return "new $className(\n${renderedArguments.joinToString("\n")}\n${indent(indentLevel)})"
}

private fun indent(level: Int): String {
    return "  ".repeat(level)
}
