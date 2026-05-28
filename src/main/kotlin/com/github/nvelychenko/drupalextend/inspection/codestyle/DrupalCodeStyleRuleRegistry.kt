package com.github.nvelychenko.drupalextend.inspection.codestyle

internal object DrupalCodeStyleRuleRegistry {
    val rules: List<DrupalCodeStyleRule> = listOf(
        ClassFileNameRule,
        MissingDocCommentRule,
        FunctionSpacingRule,
        ClassCloseBraceSpacingRule,
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
    )
}
