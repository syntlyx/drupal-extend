package com.github.nvelychenko.drupalextend.inspection.codestyle

internal object DrupalCodeStyleRuleRegistry {
    val rules: List<DrupalCodeStyleRule> = listOf(
        ClassFileNameRule,
        ClassNameRule,
        FunctionNameRule,
        ConstantCasingRule,
        ClosingPhpTagRule,
        ShortArraySyntaxRule,
        ElseIfSyntaxRule,
        MissingDocCommentRule,
        FunctionSpacingRule,
        ClassCloseBraceSpacingRule,
        OperatorSpacingRule,
        CommaSpacingRule,
        ControlStructureSpacingRule,
        UseStatementSortingRule,
        DocBlockSummaryPunctuationRule,
        DocBlockBlankLineBeforeTagsRule,
        DocBlockTagGroupSpacingRule,
        DocBlockBlankLineNormalizationRule,
        DocBlockContentAfterOpenRule,
        DocBlockMissingSummaryRule,
        FunctionDocParamNameRule,
        FunctionDocTagDescriptionRequiredRule,
        FunctionDocTagDescriptionIndentationRule,
        FunctionDocTagDescriptionLineRule,
        FunctionDocTagDescriptionPunctuationRule,
    )
}
