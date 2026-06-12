package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.github.nvelychenko.drupalextend.project.DrupalCodeStyleSeverity
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList
import com.jetbrains.php.lang.psi.elements.PhpPropertyHook
import com.jetbrains.php.lang.psi.elements.PhpSwitch
import com.jetbrains.php.lang.psi.elements.PhpUseList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.parser.PhpElementTypes

internal object DrupalCodeStyleRuleRegistry {
    private val registrations = listOf(
        register(ClassFileNameRule, { it.codeStyleClassFileNameSeverity }, Target.PHP_CLASS),
        register(ClassNameRule, { it.codeStyleClassNameSeverity }, Target.PHP_CLASS),
        register(FunctionNameRule, { it.codeStyleFunctionNameSeverity }, Target.FUNCTION),
        register(MemberVisibilityRule, { it.codeStyleMemberVisibilitySeverity }, Target.PHP_CLASS, Target.METHOD, Target.CLASS_FIELDS),
        register(ConstantCasingRule, { it.codeStyleConstantCasingSeverity }, Target.CONSTANT_REFERENCE),
        register(ClosingPhpTagRule, { it.codeStyleClosingPhpTagSeverity }, Target.PHP_FILE),
        register(FileWhitespaceRule, { it.codeStyleFileWhitespaceSeverity }, Target.PHP_FILE),
        register(ShortArraySyntaxRule, { it.codeStyleShortArraySyntaxSeverity }, Target.ARRAY_CREATION),
        register(MultilineArrayStyleRule, { it.codeStyleMultilineArrayStyleSeverity }, Target.ARRAY_CREATION),
        register(ElseIfSyntaxRule, { it.codeStyleElseIfSyntaxSeverity }, Target.ELSE),
        register(FullyQualifiedClassNameRule, { it.codeStyleFullyQualifiedClassNameSeverity }, Target.CLASS_REFERENCE),
        register(MissingDocCommentRule, { it.codeStyleMissingDocCommentsSeverity }, Target.PHP_CLASS, Target.FUNCTION, Target.CLASS_FIELDS),
        register(FunctionDeclarationStyleRule, { it.codeStyleFunctionDeclarationStyleSeverity }, Target.FUNCTION),
        register(FunctionSpacingRule, { it.codeStyleFunctionSpacingSeverity }, Target.FUNCTION),
        register(ClassCloseBraceSpacingRule, { it.codeStyleClassCloseBraceSpacingSeverity }, Target.PHP_CLASS),
        register(BlockBlankLineRule, { it.codeStyleBlockBlankLinesSeverity }, Target.FUNCTION, Target.CONTROL_STATEMENT, Target.ELSE, Target.SWITCH),
        register(CastSpacingRule, { it.codeStyleCastSpacingSeverity }, Target.CAST_EXPRESSION),
        register(OperatorSpacingRule, { it.codeStyleOperatorSpacingSeverity }, Target.OPERATOR),
        register(CommaSpacingRule, { it.codeStyleCommaSpacingSeverity }, Target.ARRAY_CREATION, Target.PARAMETER_LIST),
        register(ControlStructureSpacingRule, { it.codeStyleControlStructureSpacingSeverity }, Target.CONTROL_STATEMENT, Target.ELSE, Target.SWITCH),
        register(UseStatementSortingRule, { it.codeStyleUseStatementSortingSeverity }, Target.USE_LIST),
        register(DocBlockSummaryPunctuationRule, { it.codeStyleDocBlockSummaryPunctuationSeverity }, Target.DOC_BLOCK),
        register(DocBlockBlankLineBeforeTagsRule, { it.codeStyleDocBlockBlankLineBeforeTagsSeverity }, Target.DOC_BLOCK),
        register(DocBlockTagGroupSpacingRule, { it.codeStyleDocBlockTagGroupSpacingSeverity }, Target.DOC_BLOCK),
        register(DocBlockBlankLineNormalizationRule, { it.codeStyleDocBlockBlankLineNormalizationSeverity }, Target.DOC_BLOCK),
        register(DocBlockDeclarationSpacingRule, { it.codeStyleDocBlockDeclarationSpacingSeverity }, Target.DOC_BLOCK),
        register(DocBlockContentAfterOpenRule, { it.codeStyleDocBlockContentAfterOpenSeverity }, Target.DOC_BLOCK),
        register(DocBlockMissingSummaryRule, { it.codeStyleDocBlockMissingSummarySeverity }, Target.DOC_BLOCK),
        register(FunctionDocParamNameRule, { it.codeStyleDocBlockMissingParamNameSeverity }, Target.FUNCTION),
        register(FunctionDocTagDescriptionRequiredRule, { it.codeStyleDocBlockTagDescriptionRequiredSeverity }, Target.FUNCTION),
        register(FunctionDocTagDescriptionIndentationRule, { it.codeStyleDocBlockTagDescriptionIndentationSeverity }, Target.FUNCTION),
        register(FunctionDocTagDescriptionLineRule, { it.codeStyleDocBlockTagDescriptionLineSeverity }, Target.FUNCTION),
        register(FunctionDocTagDescriptionPunctuationRule, { it.codeStyleDocBlockTagDescriptionPunctuationSeverity }, Target.FUNCTION),
        register(DocBlockApiTagSyntaxRule, { it.codeStyleDocBlockApiTagsSeverity }, Target.DOC_BLOCK),
        register(FileDocCommentRule, { it.codeStyleFileDocCommentSeverity }, Target.PHP_FILE),
        // CONTROL_STATEMENT too: the one-keyword `elseif` form is an ElseIf (an If subtype),
        // never routed through the ELSE slot.
        register(ElsePlacementRule, { it.codeStyleElsePlacementSeverity }, Target.ELSE, Target.CONTROL_STATEMENT),
        register(StringQuotesRule, { it.codeStyleStringQuotesSeverity }, Target.STRING_LITERAL),
        register(InlineCommentRule, { it.codeStyleInlineCommentSeverity }, Target.COMMENT),
        register(DiscouragedFunctionsRule, { it.codeStyleDiscouragedFunctionsSeverity }, Target.FUNCTION_REFERENCE),
        register(PropertyNameRule, { it.codeStylePropertyNameSeverity }, Target.CLASS_FIELDS),
        register(ScopeKeywordSpacingRule, { it.codeStyleScopeKeywordSpacingSeverity }, Target.METHOD, Target.CLASS_FIELDS),
        register(ConsecutiveBlankLinesRule, { it.codeStyleConsecutiveBlankLinesSeverity }, Target.PHP_FILE),
        register(DocBlockShortTypesRule, { it.codeStyleDocBlockShortTypesSeverity }, Target.DOC_BLOCK),
        register(DocBlockSummaryLengthRule, { it.codeStyleDocBlockSummaryLengthSeverity }, Target.DOC_BLOCK),
        register(FunctionDocMissingTagsRule, { it.codeStyleFunctionDocMissingTagsSeverity }, Target.FUNCTION),
    )

    val rules: List<DrupalCodeStyleRule> = registrations.map { it.rule }

    fun dispatcher(settings: DrupalExtendSettings): DrupalCodeStyleRuleDispatcher {
        val enabledRegistrations = registrations
            .filter { it.severity(settings) != DrupalCodeStyleSeverity.OFF }
        val severityOverrides = enabledRegistrations.associate { registration ->
            registration.rule to registration.severity(settings).toProblemHighlightType()
        }

        return DrupalCodeStyleRuleDispatcher(
            severityOverrides = severityOverrides,
            fileRules = enabledRegistrations.rulesFor(Target.PHP_FILE),
            classRules = enabledRegistrations.rulesFor(Target.PHP_CLASS),
            functionRules = enabledRegistrations.rulesFor(Target.FUNCTION),
            methodRules = enabledRegistrations.rulesFor { Target.FUNCTION in it || Target.METHOD in it },
            classFieldRules = enabledRegistrations.rulesFor(Target.CLASS_FIELDS),
            docBlockRules = enabledRegistrations.rulesFor(Target.DOC_BLOCK),
            classReferenceRules = enabledRegistrations.rulesFor(Target.CLASS_REFERENCE),
            constantReferenceRules = enabledRegistrations.rulesFor(Target.CONSTANT_REFERENCE),
            arrayCreationRules = enabledRegistrations.rulesFor(Target.ARRAY_CREATION),
            parameterListRules = enabledRegistrations.rulesFor(Target.PARAMETER_LIST),
            operatorRules = enabledRegistrations.rulesFor(Target.OPERATOR),
            castExpressionRules = enabledRegistrations.rulesFor(Target.CAST_EXPRESSION),
            controlStatementRules = enabledRegistrations.rulesFor(Target.CONTROL_STATEMENT),
            elseRules = enabledRegistrations.rulesFor(Target.ELSE),
            switchRules = enabledRegistrations.rulesFor(Target.SWITCH),
            useListRules = enabledRegistrations.rulesFor(Target.USE_LIST),
            stringLiteralRules = enabledRegistrations.rulesFor(Target.STRING_LITERAL),
            commentRules = enabledRegistrations.rulesFor(Target.COMMENT),
            functionReferenceRules = enabledRegistrations.rulesFor(Target.FUNCTION_REFERENCE),
        )
    }

    private fun register(
        rule: DrupalCodeStyleRule,
        severity: (DrupalExtendSettings) -> DrupalCodeStyleSeverity,
        vararg targets: Target,
    ): DrupalCodeStyleRuleRegistration {
        return DrupalCodeStyleRuleRegistration(rule, severity, targets.toSet())
    }
}

private typealias Target = DrupalCodeStyleRuleTarget

private data class DrupalCodeStyleRuleRegistration(
    val rule: DrupalCodeStyleRule,
    val severity: (DrupalExtendSettings) -> DrupalCodeStyleSeverity,
    val targets: Set<Target>,
)

private enum class DrupalCodeStyleRuleTarget {
    PHP_FILE,
    PHP_CLASS,
    FUNCTION,
    METHOD,
    CLASS_FIELDS,
    DOC_BLOCK,
    CLASS_REFERENCE,
    CONSTANT_REFERENCE,
    ARRAY_CREATION,
    PARAMETER_LIST,
    OPERATOR,
    CAST_EXPRESSION,
    CONTROL_STATEMENT,
    ELSE,
    SWITCH,
    USE_LIST,
    STRING_LITERAL,
    COMMENT,
    FUNCTION_REFERENCE,
}

private fun List<DrupalCodeStyleRuleRegistration>.rulesFor(
    target: Target,
): List<DrupalCodeStyleRule> {
    return rulesFor { target in it }
}

private fun List<DrupalCodeStyleRuleRegistration>.rulesFor(
    acceptsTargets: (Set<Target>) -> Boolean,
): List<DrupalCodeStyleRule> {
    return filter { acceptsTargets(it.targets) }
        .map { it.rule }
}

internal class DrupalCodeStyleRuleDispatcher(
    private val severityOverrides: Map<DrupalCodeStyleRule, ProblemHighlightType?>,
    private val fileRules: List<DrupalCodeStyleRule>,
    private val classRules: List<DrupalCodeStyleRule>,
    private val functionRules: List<DrupalCodeStyleRule>,
    private val methodRules: List<DrupalCodeStyleRule>,
    private val classFieldRules: List<DrupalCodeStyleRule>,
    private val docBlockRules: List<DrupalCodeStyleRule>,
    private val classReferenceRules: List<DrupalCodeStyleRule>,
    private val constantReferenceRules: List<DrupalCodeStyleRule>,
    private val arrayCreationRules: List<DrupalCodeStyleRule>,
    private val parameterListRules: List<DrupalCodeStyleRule>,
    private val operatorRules: List<DrupalCodeStyleRule>,
    private val castExpressionRules: List<DrupalCodeStyleRule>,
    private val controlStatementRules: List<DrupalCodeStyleRule>,
    private val elseRules: List<DrupalCodeStyleRule>,
    private val switchRules: List<DrupalCodeStyleRule>,
    private val useListRules: List<DrupalCodeStyleRule>,
    private val stringLiteralRules: List<DrupalCodeStyleRule>,
    private val commentRules: List<DrupalCodeStyleRule>,
    private val functionReferenceRules: List<DrupalCodeStyleRule>,
) {
    val isEmpty: Boolean = listOf(
        fileRules,
        classRules,
        functionRules,
        methodRules,
        classFieldRules,
        docBlockRules,
        classReferenceRules,
        constantReferenceRules,
        arrayCreationRules,
        parameterListRules,
        operatorRules,
        castExpressionRules,
        controlStatementRules,
        elseRules,
        switchRules,
        useListRules,
        stringLiteralRules,
        commentRules,
        functionReferenceRules,
    ).all { it.isEmpty() }

    fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        when (element) {
            is PhpFile -> fileRules.inspect(element, context)
            is PhpClass -> classRules.inspect(element, context)
            is PhpPropertyHook -> Unit
            is Method -> methodRules.inspect(element, context)
            is Function -> functionRules.inspect(element, context)
            is PhpClassFieldsList -> classFieldRules.inspect(element, context)
            is PhpDocComment -> docBlockRules.inspect(element, context)
            // After PhpDocComment: docblocks are PsiComments too and must not reach inline rules.
            is PsiComment -> commentRules.inspect(element, context)
            is StringLiteralExpression -> stringLiteralRules.inspect(element, context)
            // Matches MethodReference too; function-reference rules narrow themselves.
            is FunctionReference -> functionReferenceRules.inspect(element, context)
            is ClassReference -> classReferenceRules.inspect(element, context)
            is ConstantReference -> constantReferenceRules.inspect(element, context)
            is ArrayCreationExpression -> arrayCreationRules.inspect(element, context)
            is ParameterList -> parameterListRules.inspect(element, context)
            is AssignmentExpression -> operatorRules.inspect(element, context)
            is ArrayHashElement -> operatorRules.inspect(element, context)
            is BinaryExpression -> operatorRules.inspect(element, context)
            else -> {
                if (element.node?.elementType == PhpElementTypes.CAST_EXPRESSION) {
                    castExpressionRules.inspect(element, context)
                }
            }
        }

        when (element) {
            is Else -> elseRules.inspect(element, context)
            is ControlStatement -> controlStatementRules.inspect(element, context)
            is PhpSwitch -> switchRules.inspect(element, context)
            is PhpUseList -> useListRules.inspect(element, context)
        }
    }

    private fun List<DrupalCodeStyleRule>.inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        forEach { rule ->
            rule.inspect(element, context.withSeverityOverride(severityOverrides[rule]))
        }
    }
}
