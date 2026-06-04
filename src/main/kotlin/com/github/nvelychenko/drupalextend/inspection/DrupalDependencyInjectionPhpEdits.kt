package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.escapePhpSingleQuoted
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList
import com.jetbrains.php.lang.psi.elements.PhpUseList

internal const val CONTAINER_INTERFACE = "\\Symfony\\Component\\DependencyInjection\\ContainerInterface"

private const val CONTAINER_INJECTION_INTERFACE = "\\Drupal\\Core\\DependencyInjection\\ContainerInjectionInterface"
private val CONTAINER_INJECTABLE_BASE_CLASSES = setOf(
    "\\Drupal\\Core\\Controller\\ControllerBase",
    "\\Drupal\\Core\\Form\\FormBase",
    "\\Drupal\\Core\\Form\\ConfigFormBase",
    "\\Drupal\\Core\\Form\\ConfirmFormBase",
)

/**
 * Offset just after the opening `{` of the class body.
 *
 * Located via PSI rather than a raw text scan: a class-level PHP 8 attribute whose argument
 * contains a `{` (e.g. `#[Some\Attr('{token}')]`) would make `text.indexOf('{')` land inside the
 * attribute, so members would be injected into the attribute and produce unparseable PHP.
 * The body brace is the first [PhpTokenTypes.chLBRACE] leaf among the class node's direct children;
 * the attribute's brace lives nested inside the `PhpAttributesList` subtree, never as a direct
 * leaf of the class node. Falls back to the old text scan if no brace leaf is found.
 */
internal fun PhpClass.classBodyStartOffset(): Int? {
    // The body brace is a direct AST child of the class node, placed after the header and any
    // PhpAttributesList; the attribute's own brace lives nested in that subtree, so findChildByType
    // never reaches it. (Mirrors how PhpClassImpl locates its braces.)
    node?.findChildByType(PhpTokenTypes.chLBRACE)?.textRange?.let { return it.endOffset }

    val bodyStartInClass = text.indexOf('{')
    if (bodyStartInClass < 0) return null

    return textRange.startOffset + bodyStartInClass + 1
}

/**
 * Offset where new class members (property/constructor) may be inserted.
 *
 * PHP requires class-level trait `use Trait;` statements to be the first members of the
 * class body, so the anchor is placed after the last such statement when present;
 * otherwise it falls back to right after the opening `{`.
 */
internal fun PhpClass.serviceInjectionAnchorOffset(): Int? {
    val bodyStart = classBodyStartOffset() ?: return null
    val lastTraitUse = PsiTreeUtil.getChildrenOfTypeAsList(this, PhpUseList::class.java)
        .filter { it.isTraitImport }
        .maxByOrNull { it.textRange.endOffset }
        ?: return bodyStart

    // The PhpUseList element may or may not include its terminating `;`; extend past a trailing
    // `;` leaf so the anchor never lands in the middle of a statement.
    val lastTraitUseEnd = lastTraitUse.endOffsetIncludingTrailingSemicolon()

    return if (lastTraitUseEnd > bodyStart) lastTraitUseEnd else bodyStart
}

private fun PsiElement.endOffsetIncludingTrailingSemicolon(): Int {
    val next = nextSibling
    if (next != null && next.text == ";") {
        return next.textRange.endOffset
    }
    return textRange.endOffset
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

private const val DEFAULT_MEMBER_INDENT = "  "
private const val DEFAULT_BODY_INDENT = "    "

/**
 * Best-effort leading indentation of class members, inferred from an existing own field or
 * method. Falls back to [DEFAULT_MEMBER_INDENT] when nothing can be determined.
 */
internal fun PhpClass.memberIndent(): String {
    val firstFieldDeclaration = PsiTreeUtil.getChildrenOfTypeAsList(this, PhpClassFieldsList::class.java).firstOrNull()
    val anchor = firstFieldDeclaration ?: getOwnMethods().firstOrNull()
    return anchor?.let { indentBefore(it) } ?: DEFAULT_MEMBER_INDENT
}

/**
 * Best-effort leading indentation of statements inside the constructor body, inferred from the
 * first statement. Falls back to [DEFAULT_BODY_INDENT] when nothing can be determined.
 */
internal fun Method.bodyStatementIndent(): String {
    val body = PsiTreeUtil.findChildOfType(this, GroupStatement::class.java)
    val firstStatement = body?.statements?.firstOrNull()
    return firstStatement?.let { indentBefore(it) } ?: DEFAULT_BODY_INDENT
}

/** Leading whitespace on the line that [element] starts on, derived from its preceding whitespace. */
private fun indentBefore(element: PsiElement): String? {
    val whitespace = (element.prevSibling as? PsiWhiteSpace)?.text ?: return null
    return whitespace.substringAfterLast('\n').takeIf { it.isNotEmpty() && it.all { ch -> ch == ' ' || ch == '\t' } }
}

internal fun DrupalStaticServiceCall.newPropertyText(serviceType: String, indent: String): String {
    return "\n$indent" + $$"protected $$serviceType $$$propertyName;\n"
}

internal fun DrupalStaticServiceCall.newPropertyAndConstructorText(
    serviceType: String,
    containerType: String?,
    memberIndent: String = DEFAULT_MEMBER_INDENT,
    bodyIndent: String = DEFAULT_BODY_INDENT,
): String {
    return "\n" +
            "$memberIndent" + $$"protected $$serviceType $$$propertyName;\n" +
            "\n" +
            "$memberIndent" + $$"public function __construct($$serviceType $$$parameterName) {\n" +
            "$bodyIndent" + $$"$this->$$propertyName = $$$parameterName;\n" +
            "$memberIndent}\n" +
            (containerType?.let { newCreateMethodText(it, memberIndent, bodyIndent) } ?: "")
}

/**
 * Renders a `create()` override that builds the instance through the container.
 *
 * [existingParameterNames] are the names of the parameters the (existing) constructor already
 * declares, in order. We cannot know how those values are sourced from the container, so each is
 * emitted as a `null /* TODO: provide $<name> */` placeholder before the newly injected service.
 * When the constructor is parameterless the method is clean: just `$container->get('<id>')`.
 */
internal fun DrupalStaticServiceCall.newCreateMethodText(
    containerType: String,
    memberIndent: String,
    bodyIndent: String,
    existingParameterNames: List<String> = emptyList(),
): String {
    val step = (bodyIndent.length - memberIndent.length).coerceAtLeast(0)
    val innerIndent = bodyIndent + (bodyIndent.firstOrNull()?.toString().orEmpty().repeat(step))
    val serviceArgument = $$"$container->get('$${serviceId.escapePhpSingleQuoted()}')"

    val argumentLines = buildString {
        for (name in existingParameterNames) {
            append(innerIndent).append("null /* TODO: provide $").append(name).append(" */,\n")
        }
        append(innerIndent).append(serviceArgument).append(",\n")
    }
    val leadingTodo = if (existingParameterNames.isEmpty()) {
        ""
    } else {
        "$bodyIndent// TODO: provide the existing constructor arguments through the container.\n"
    }

    return "\n" +
            "$memberIndent" + $$"public static function create($$containerType $container): static {\n" +
            leadingTodo +
            "$bodyIndent" + "return new static(\n" +
            argumentLines +
            "$bodyIndent);\n" +
            "$memberIndent}\n"
}

/**
 * Detects whether the constructor uses constructor property promotion (any parameter declared with
 * a visibility modifier). When promoted, the dependency should be added as a promoted parameter
 * rather than a separate field + assignment.
 */
internal fun Method.usesConstructorPromotion(): Boolean {
    return parameters.any { it.isPromotedField }
}

internal fun Method.parameterInsertionEdit(
    serviceCall: DrupalStaticServiceCall,
    serviceType: String,
    promoted: Boolean,
): TextEdit? {
    // A promoted parameter IS the property, so it follows the camelCase property naming used by
    // modern Drupal core; a classic parameter keeps the snake_case argument convention.
    val parameterName = if (promoted) serviceCall.propertyName else serviceCall.parameterName
    if (parameters.any { it.name == parameterName }) return null
    val parameterList = PsiTreeUtil.findChildOfType(this, ParameterList::class.java) ?: return null
    val existingParameters = parameterList.parameters
    val declaration = if (promoted) {
        $$"protected $$serviceType $$$parameterName"
    } else {
        $$"$$serviceType $$$parameterName"
    }

    if (existingParameters.isEmpty()) {
        return TextEdit(parameterList.textRange.endOffset, declaration)
    }

    val lastParameter = existingParameters.last()
    val separator = interParameterSeparator(existingParameters)

    return TextEdit(lastParameter.textRange.endOffset, "$separator$declaration")
}

/**
 * Separator to place before an appended parameter. Mirrors the existing inter-parameter style:
 * the newline-based separator for a multiline signature, otherwise `", "`. The new parameter is
 * inserted directly after the last existing [Parameter] element, so any trailing comma in the
 * source is preserved untouched.
 */
private fun interParameterSeparator(existingParameters: Array<out PsiElement>): String {
    if (existingParameters.size >= 2) {
        val firstEnd = existingParameters[0].textRange.endOffset
        val secondStart = existingParameters[1].textRange.startOffset
        val between = existingParameters[0].containingFile?.text?.substring(firstEnd, secondStart)
        if (between != null && between.contains('\n')) {
            return ",$between"
        }
    }
    return ", "
}

internal fun Method.assignmentInsertionEdit(serviceCall: DrupalStaticServiceCall, bodyIndent: String): TextEdit? {
    if (text.contains($$"$this->$${serviceCall.propertyName} =")) return null
    val body = PsiTreeUtil.findChildOfType(this, GroupStatement::class.java) ?: return null
    val closingIndent = (body.lastChild?.prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n').orEmpty()

    return TextEdit(
        body.textRange.endOffset - 1,
        "\n$bodyIndent" + $$"$this->$${serviceCall.propertyName} = $$${serviceCall.parameterName};\n$$closingIndent"
    )
}

internal fun Method.staticCreateInsertionEdit(serviceCall: DrupalStaticServiceCall): TextEdit? {
    if (!isStatic || text.contains("get('${serviceCall.serviceId.escapePhpSingleQuoted()}')")) return null
    val containerParameterName = parameters.firstOrNull()?.name
    val constructorCall = findReturnedConstructorCall()
        // Indirect body: leave a TODO referencing the actual container parameter, or `$container`.
        ?: return indirectCreateTodoEdit(serviceCall, containerParameterName ?: "container")
    // Direct `return new static(...)`: wire the service in, but only if we have a container variable
    // to read from — otherwise we'd reference an undefined variable.
    val containerName = containerParameterName ?: return null
    val constructorArguments = text.substring(constructorCall.openParenOffset + 1, constructorCall.closeParenOffset).trim()
    val prefix = if (constructorArguments.isBlank()) "" else ", "

    return TextEdit(
        textRange.startOffset + constructorCall.closeParenOffset,
        $$"$$prefix$$$containerName->get('$${serviceCall.serviceId.escapePhpSingleQuoted()}')"
    )
}

/**
 * Fallback for an own `create()` whose body builds the instance indirectly (no direct
 * `return new static(...)`). We can't safely rewrite the indirect construction, so we mark it with
 * a `// TODO` as the first body statement; the constructor still gains the new parameter, so the
 * developer is pointed at exactly what needs wiring. Returns null if the body can't be located.
 */
private fun Method.indirectCreateTodoEdit(serviceCall: DrupalStaticServiceCall, containerName: String): TextEdit? {
    val body = PsiTreeUtil.findChildOfType(this, GroupStatement::class.java) ?: return null
    val statementIndent = bodyStatementIndent()
    val todo = "// TODO: pass \$$containerName->get('${serviceCall.serviceId.escapePhpSingleQuoted()}')" +
            " to the constructor call below."

    return TextEdit(
        body.textRange.startOffset + 1,
        "\n$statementIndent$todo"
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
