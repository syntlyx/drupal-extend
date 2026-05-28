package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.escapePhpSingleQuoted
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass

internal const val CONTAINER_INTERFACE = "\\Symfony\\Component\\DependencyInjection\\ContainerInterface"

private const val CONTAINER_INJECTION_INTERFACE = "\\Drupal\\Core\\DependencyInjection\\ContainerInjectionInterface"
private val CONTAINER_INJECTABLE_BASE_CLASSES = setOf(
    "\\Drupal\\Core\\Controller\\ControllerBase",
    "\\Drupal\\Core\\Form\\FormBase",
    "\\Drupal\\Core\\Form\\ConfigFormBase",
    "\\Drupal\\Core\\Form\\ConfirmFormBase",
)

internal fun PhpClass.classBodyStartOffset(): Int? {
    val classText = text
    val bodyStartInClass = classText.indexOf('{')
    if (bodyStartInClass < 0) return null

    return textRange.startOffset + bodyStartInClass + 1
}

internal fun PhpClass.isContainerInjectionTarget(): Boolean {
    val superFqns = buildList {
        addAll(superClasses.map { it.fqn })
        addAll(implementedInterfaces.map { it.fqn })
        addAll(listOfNotNull(superFQN))
        addAll(interfaceNames.toList())
    }
        .map { it.normalizeServiceClassFqn() }
        .toSet()

    return CONTAINER_INJECTION_INTERFACE in superFqns || superFqns.any { it in CONTAINER_INJECTABLE_BASE_CLASSES }
}

internal fun DrupalStaticServiceCall.newPropertyText(serviceType: String): String {
    return $$"\n  protected $$serviceType $$$propertyName;\n"
}

internal fun DrupalStaticServiceCall.newPropertyAndConstructorText(serviceType: String, containerType: String?): String {
    return "\n" +
            $$"  protected $$serviceType $$$propertyName;\n" +
            "\n" +
            $$"  public function __construct($$serviceType $$$parameterName) {\n" +
            $$"    $this->$$propertyName = $$$parameterName;\n" +
            "  }\n" +
            (containerType?.let { newCreateMethodText(it) } ?: "")
}

private fun DrupalStaticServiceCall.newCreateMethodText(containerType: String): String {
    return "\n" +
            $$"  public static function create($$containerType $container): static {\n" +
            "    return new static(\n" +
            $$"      $container->get('$${serviceId.escapePhpSingleQuoted()}'),\n" +
            "    );\n" +
            "  }\n"
}

internal fun Method.parameterInsertionEdit(serviceCall: DrupalStaticServiceCall, serviceType: String): TextEdit? {
    if (parameters.any { it.name == serviceCall.parameterName }) return null
    val parameterList = PsiTreeUtil.findChildOfType(this, ParameterList::class.java) ?: return null
    val prefix = if (parameters.isEmpty()) "" else ", "

    return TextEdit(
        parameterList.textRange.endOffset,
        $$"$$prefix$$serviceType $$${serviceCall.parameterName}"
    )
}

internal fun Method.assignmentInsertionEdit(serviceCall: DrupalStaticServiceCall): TextEdit? {
    if (text.contains($$"$this->$${serviceCall.propertyName} =")) return null
    val body = PsiTreeUtil.findChildOfType(this, GroupStatement::class.java) ?: return null

    return TextEdit(
        body.textRange.endOffset - 1,
        $$"\n    $this->$${serviceCall.propertyName} = $$${serviceCall.parameterName};\n  "
    )
}

internal fun Method.staticCreateInsertionEdit(serviceCall: DrupalStaticServiceCall): TextEdit? {
    if (!isStatic || text.contains("get('${serviceCall.serviceId.escapePhpSingleQuoted()}')")) return null
    val parameterName = parameters.firstOrNull()?.name ?: return null
    val constructorCall = findReturnedConstructorCall() ?: return null
    val constructorArguments = text.substring(constructorCall.openParenOffset + 1, constructorCall.closeParenOffset).trim()
    val prefix = if (constructorArguments.isBlank()) "" else ", "

    return TextEdit(
        textRange.startOffset + constructorCall.closeParenOffset,
        $$"$$prefix$$$parameterName->get('$${serviceCall.serviceId.escapePhpSingleQuoted()}')"
    )
}

private data class ConstructorCallRange(
    val openParenOffset: Int,
    val closeParenOffset: Int,
)

private fun Method.findReturnedConstructorCall(): ConstructorCallRange? {
    val constructorReferences = constructorReferencePatterns().joinToString("|").takeIf { it.isNotBlank() } ?: return null
    val match = Regex("""return\s+new\s+(?:$constructorReferences)\s*\(""").find(text) ?: return null
    val openParenOffset = match.range.last
    val closeParenOffset = text.findMatchingParen(openParenOffset) ?: return null

    return ConstructorCallRange(openParenOffset, closeParenOffset)
}

private fun Method.constructorReferencePatterns(): List<String> {
    val phpClass = containingClass ?: return listOf("static", "self")
    val shortName = phpClass.name.takeIf { it.isNotBlank() }
    val fqn = phpClass.fqn.removePrefix("\\").takeIf { it.isNotBlank() && it != shortName }

    return buildList {
        add("static")
        add("self")
        shortName?.let { add(Regex.escape(it)) }
        fqn?.let { add("""\\?${Regex.escape(it)}""") }
    }.distinct()
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
