package com.github.nvelychenko.drupalextend.project

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

private const val SERVICE_NAME: String = "DrupalExtendSettings"

val Project.drupalExtendSettings: DrupalExtendSettings
    get() = service<DrupalExtendSettings>()

@Service
@State(name = SERVICE_NAME, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DrupalExtendSettings :
    SimplePersistentStateComponent<DrupalExtendSettings.DrupalExtendState>(DrupalExtendState()) {

    var isEnabled: Boolean
        get() = state.isEnabled
        set(value) {
            state.isEnabled = value
        }

    var isEntityReferenceTypeResolverEnabled: Boolean
        get() = state.isEntityReferenceTypeResolverEnabled
        set(value) {
            state.isEntityReferenceTypeResolverEnabled = value
        }

    var isServicesCompletionEnabled: Boolean
        get() = state.isServicesCompletionEnabled
        set(value) {
            state.isServicesCompletionEnabled = value
        }

    var isRoutesCompletionEnabled: Boolean
        get() = state.isRoutesCompletionEnabled
        set(value) {
            state.isRoutesCompletionEnabled = value
        }

    var isDependencyInjectionHelperEnabled: Boolean
        get() = state.isDependencyInjectionHelperEnabled
        set(value) {
            state.isDependencyInjectionHelperEnabled = value
        }

    var isCodeStyleLinterEnabled: Boolean
        get() = state.isCodeStyleLinterEnabled
        set(value) {
            state.isCodeStyleLinterEnabled = value
        }

    var codeStyleDocBlockSummaryPunctuationSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockSummaryPunctuationSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockSummaryPunctuationSeverity = value.name
        }

    var isCodeStyleDocBlockSummaryPunctuationEnabled: Boolean
        get() = codeStyleDocBlockSummaryPunctuationSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockSummaryPunctuationSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockBlankLineBeforeTagsSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockBlankLineBeforeTagsSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockBlankLineBeforeTagsSeverity = value.name
        }

    var isCodeStyleDocBlockBlankLineBeforeTagsEnabled: Boolean
        get() = codeStyleDocBlockBlankLineBeforeTagsSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockBlankLineBeforeTagsSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockTagGroupSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockTagGroupSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockTagGroupSpacingSeverity = value.name
        }

    var isCodeStyleDocBlockTagGroupSpacingEnabled: Boolean
        get() = codeStyleDocBlockTagGroupSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockTagGroupSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockBlankLineNormalizationSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockBlankLineNormalizationSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockBlankLineNormalizationSeverity = value.name
        }

    var isCodeStyleDocBlockBlankLineNormalizationEnabled: Boolean
        get() = codeStyleDocBlockBlankLineNormalizationSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockBlankLineNormalizationSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockContentAfterOpenSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockContentAfterOpenSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockContentAfterOpenSeverity = value.name
        }

    var isCodeStyleDocBlockContentAfterOpenEnabled: Boolean
        get() = codeStyleDocBlockContentAfterOpenSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockContentAfterOpenSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockMissingSummarySeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockMissingSummarySeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockMissingSummarySeverity = value.name
        }

    var isCodeStyleDocBlockMissingSummaryEnabled: Boolean
        get() = codeStyleDocBlockMissingSummarySeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockMissingSummarySeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockMissingParamNameSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockMissingParamNameSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockMissingParamNameSeverity = value.name
        }

    var isCodeStyleDocBlockMissingParamNameEnabled: Boolean
        get() = codeStyleDocBlockMissingParamNameSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockMissingParamNameSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockTagDescriptionIndentationSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockTagDescriptionIndentationSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockTagDescriptionIndentationSeverity = value.name
        }

    var isCodeStyleDocBlockTagDescriptionIndentationEnabled: Boolean
        get() = codeStyleDocBlockTagDescriptionIndentationSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockTagDescriptionIndentationSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockTagDescriptionRequiredSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockTagDescriptionRequiredSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockTagDescriptionRequiredSeverity = value.name
        }

    var isCodeStyleDocBlockTagDescriptionRequiredEnabled: Boolean
        get() = codeStyleDocBlockTagDescriptionRequiredSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockTagDescriptionRequiredSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockTagDescriptionLineSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockTagDescriptionLineSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockTagDescriptionLineSeverity = value.name
        }

    var isCodeStyleDocBlockTagDescriptionLineEnabled: Boolean
        get() = codeStyleDocBlockTagDescriptionLineSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockTagDescriptionLineSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockTagDescriptionPunctuationSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockTagDescriptionPunctuationSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockTagDescriptionPunctuationSeverity = value.name
        }

    var isCodeStyleDocBlockTagDescriptionPunctuationEnabled: Boolean
        get() = codeStyleDocBlockTagDescriptionPunctuationSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockTagDescriptionPunctuationSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockApiTagsSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockApiTagsSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockApiTagsSeverity = value.name
        }

    var isCodeStyleDocBlockApiTagsEnabled: Boolean
        get() = codeStyleDocBlockApiTagsSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockApiTagsSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleClassFileNameSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleClassFileNameSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleClassFileNameSeverity = value.name
        }

    var isCodeStyleClassFileNameEnabled: Boolean
        get() = codeStyleClassFileNameSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleClassFileNameSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleClassNameSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleClassNameSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleClassNameSeverity = value.name
        }

    var isCodeStyleClassNameEnabled: Boolean
        get() = codeStyleClassNameSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleClassNameSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFunctionNameSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFunctionNameSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFunctionNameSeverity = value.name
        }

    var isCodeStyleFunctionNameEnabled: Boolean
        get() = codeStyleFunctionNameSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFunctionNameSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleMemberVisibilitySeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleMemberVisibilitySeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleMemberVisibilitySeverity = value.name
        }

    var isCodeStyleMemberVisibilityEnabled: Boolean
        get() = codeStyleMemberVisibilitySeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleMemberVisibilitySeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleConstantCasingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleConstantCasingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleConstantCasingSeverity = value.name
        }

    var isCodeStyleConstantCasingEnabled: Boolean
        get() = codeStyleConstantCasingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleConstantCasingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleClosingPhpTagSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleClosingPhpTagSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleClosingPhpTagSeverity = value.name
        }

    var isCodeStyleClosingPhpTagEnabled: Boolean
        get() = codeStyleClosingPhpTagSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleClosingPhpTagSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFileWhitespaceSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFileWhitespaceSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFileWhitespaceSeverity = value.name
        }

    var isCodeStyleFileWhitespaceEnabled: Boolean
        get() = codeStyleFileWhitespaceSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFileWhitespaceSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleShortArraySyntaxSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleShortArraySyntaxSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleShortArraySyntaxSeverity = value.name
        }

    var isCodeStyleShortArraySyntaxEnabled: Boolean
        get() = codeStyleShortArraySyntaxSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleShortArraySyntaxSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleMultilineArrayStyleSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleMultilineArrayStyleSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleMultilineArrayStyleSeverity = value.name
        }

    var isCodeStyleMultilineArrayStyleEnabled: Boolean
        get() = codeStyleMultilineArrayStyleSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleMultilineArrayStyleSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleElseIfSyntaxSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleElseIfSyntaxSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleElseIfSyntaxSeverity = value.name
        }

    var isCodeStyleElseIfSyntaxEnabled: Boolean
        get() = codeStyleElseIfSyntaxSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleElseIfSyntaxSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleMissingDocCommentsSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleMissingDocCommentsSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleMissingDocCommentsSeverity = value.name
        }

    var isCodeStyleMissingDocCommentsEnabled: Boolean
        get() = codeStyleMissingDocCommentsSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleMissingDocCommentsSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFunctionDeclarationStyleSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFunctionDeclarationStyleSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFunctionDeclarationStyleSeverity = value.name
        }

    var isCodeStyleFunctionDeclarationStyleEnabled: Boolean
        get() = codeStyleFunctionDeclarationStyleSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFunctionDeclarationStyleSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFunctionSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFunctionSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFunctionSpacingSeverity = value.name
        }

    var isCodeStyleFunctionSpacingEnabled: Boolean
        get() = codeStyleFunctionSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFunctionSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleClassCloseBraceSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleClassCloseBraceSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleClassCloseBraceSpacingSeverity = value.name
        }

    var isCodeStyleClassCloseBraceSpacingEnabled: Boolean
        get() = codeStyleClassCloseBraceSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleClassCloseBraceSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleBlockBlankLinesSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleBlockBlankLinesSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleBlockBlankLinesSeverity = value.name
        }

    var isCodeStyleBlockBlankLinesEnabled: Boolean
        get() = codeStyleBlockBlankLinesSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleBlockBlankLinesSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleCastSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleCastSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleCastSpacingSeverity = value.name
        }

    var isCodeStyleCastSpacingEnabled: Boolean
        get() = codeStyleCastSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleCastSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleOperatorSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleOperatorSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleOperatorSpacingSeverity = value.name
        }

    var isCodeStyleOperatorSpacingEnabled: Boolean
        get() = codeStyleOperatorSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleOperatorSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleCommaSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleCommaSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleCommaSpacingSeverity = value.name
        }

    var isCodeStyleCommaSpacingEnabled: Boolean
        get() = codeStyleCommaSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleCommaSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleControlStructureSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleControlStructureSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleControlStructureSpacingSeverity = value.name
        }

    var isCodeStyleControlStructureSpacingEnabled: Boolean
        get() = codeStyleControlStructureSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleControlStructureSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleUseStatementSortingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleUseStatementSortingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleUseStatementSortingSeverity = value.name
        }

    var isCodeStyleUseStatementSortingEnabled: Boolean
        get() = codeStyleUseStatementSortingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleUseStatementSortingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFullyQualifiedClassNameSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFullyQualifiedClassNameSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFullyQualifiedClassNameSeverity = value.name
        }

    var isCodeStyleFullyQualifiedClassNameEnabled: Boolean
        get() = codeStyleFullyQualifiedClassNameSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFullyQualifiedClassNameSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFileDocCommentSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFileDocCommentSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFileDocCommentSeverity = value.name
        }

    var isCodeStyleFileDocCommentEnabled: Boolean
        get() = codeStyleFileDocCommentSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFileDocCommentSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleElsePlacementSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleElsePlacementSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleElsePlacementSeverity = value.name
        }

    var isCodeStyleElsePlacementEnabled: Boolean
        get() = codeStyleElsePlacementSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleElsePlacementSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleStringQuotesSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleStringQuotesSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleStringQuotesSeverity = value.name
        }

    var isCodeStyleStringQuotesEnabled: Boolean
        get() = codeStyleStringQuotesSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleStringQuotesSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleInlineCommentSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleInlineCommentSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleInlineCommentSeverity = value.name
        }

    var isCodeStyleInlineCommentEnabled: Boolean
        get() = codeStyleInlineCommentSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleInlineCommentSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDiscouragedFunctionsSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDiscouragedFunctionsSeverity, DrupalCodeStyleSeverity.WARNING)
        set(value) {
            state.codeStyleDiscouragedFunctionsSeverity = value.name
        }

    var isCodeStyleDiscouragedFunctionsEnabled: Boolean
        get() = codeStyleDiscouragedFunctionsSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDiscouragedFunctionsSeverity = if (value) DrupalCodeStyleSeverity.WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStylePropertyNameSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStylePropertyNameSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStylePropertyNameSeverity = value.name
        }

    var isCodeStylePropertyNameEnabled: Boolean
        get() = codeStylePropertyNameSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStylePropertyNameSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleScopeKeywordSpacingSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleScopeKeywordSpacingSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleScopeKeywordSpacingSeverity = value.name
        }

    var isCodeStyleScopeKeywordSpacingEnabled: Boolean
        get() = codeStyleScopeKeywordSpacingSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleScopeKeywordSpacingSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleConsecutiveBlankLinesSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleConsecutiveBlankLinesSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleConsecutiveBlankLinesSeverity = value.name
        }

    var isCodeStyleConsecutiveBlankLinesEnabled: Boolean
        get() = codeStyleConsecutiveBlankLinesSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleConsecutiveBlankLinesSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockShortTypesSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockShortTypesSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockShortTypesSeverity = value.name
        }

    var isCodeStyleDocBlockShortTypesEnabled: Boolean
        get() = codeStyleDocBlockShortTypesSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockShortTypesSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleDocBlockSummaryLengthSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleDocBlockSummaryLengthSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleDocBlockSummaryLengthSeverity = value.name
        }

    var isCodeStyleDocBlockSummaryLengthEnabled: Boolean
        get() = codeStyleDocBlockSummaryLengthSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleDocBlockSummaryLengthSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var codeStyleFunctionDocMissingTagsSeverity: DrupalCodeStyleSeverity
        get() = DrupalCodeStyleSeverity.fromState(state.codeStyleFunctionDocMissingTagsSeverity, DrupalCodeStyleSeverity.WEAK_WARNING)
        set(value) {
            state.codeStyleFunctionDocMissingTagsSeverity = value.name
        }

    var isCodeStyleFunctionDocMissingTagsEnabled: Boolean
        get() = codeStyleFunctionDocMissingTagsSeverity != DrupalCodeStyleSeverity.OFF
        set(value) {
            codeStyleFunctionDocMissingTagsSeverity = if (value) DrupalCodeStyleSeverity.WEAK_WARNING else DrupalCodeStyleSeverity.OFF
        }

    var drupalCoreVersion: String
        get() = state.drupalCoreVersion.orEmpty()
        set(value) {
            state.drupalCoreVersion = value.trim()
        }

    class DrupalExtendState : BaseState() {
        // @todo Once we will implement balloon make it false by default
        var isEnabled: Boolean by property(true)
        var drupalCoreVersion by string()
        var codeStyleFunctionDocMissingTagsSeverity by string()
        var codeStylePropertyNameSeverity by string()
        var codeStyleScopeKeywordSpacingSeverity by string()
        var codeStyleConsecutiveBlankLinesSeverity by string()
        var codeStyleDocBlockShortTypesSeverity by string()
        var codeStyleDocBlockSummaryLengthSeverity by string()
        var codeStyleElsePlacementSeverity by string()
        var codeStyleStringQuotesSeverity by string()
        var codeStyleInlineCommentSeverity by string()
        var codeStyleDiscouragedFunctionsSeverity by string()
        var isEntityReferenceTypeResolverEnabled: Boolean by property(false)
        var isServicesCompletionEnabled: Boolean by property(true)
        var isRoutesCompletionEnabled: Boolean by property(true)
        var isDependencyInjectionHelperEnabled: Boolean by property(true)
        var isCodeStyleLinterEnabled: Boolean by property(true)
        var codeStyleDocBlockSummaryPunctuationSeverity by string()
        var codeStyleDocBlockBlankLineBeforeTagsSeverity by string()
        var codeStyleDocBlockTagGroupSpacingSeverity by string()
        var codeStyleDocBlockBlankLineNormalizationSeverity by string()
        var codeStyleDocBlockContentAfterOpenSeverity by string()
        var codeStyleDocBlockMissingSummarySeverity by string()
        var codeStyleDocBlockMissingParamNameSeverity by string()
        var codeStyleDocBlockTagDescriptionIndentationSeverity by string()
        var codeStyleDocBlockTagDescriptionRequiredSeverity by string()
        var codeStyleDocBlockTagDescriptionLineSeverity by string()
        var codeStyleDocBlockTagDescriptionPunctuationSeverity by string()
        var codeStyleDocBlockApiTagsSeverity by string()
        var codeStyleClassFileNameSeverity by string()
        var codeStyleClassNameSeverity by string()
        var codeStyleFunctionNameSeverity by string()
        var codeStyleMemberVisibilitySeverity by string()
        var codeStyleConstantCasingSeverity by string()
        var codeStyleClosingPhpTagSeverity by string()
        var codeStyleFileWhitespaceSeverity by string()
        var codeStyleShortArraySyntaxSeverity by string()
        var codeStyleMultilineArrayStyleSeverity by string()
        var codeStyleElseIfSyntaxSeverity by string()
        var codeStyleMissingDocCommentsSeverity by string()
        var codeStyleFunctionDeclarationStyleSeverity by string()
        var codeStyleFunctionSpacingSeverity by string()
        var codeStyleClassCloseBraceSpacingSeverity by string()
        var codeStyleBlockBlankLinesSeverity by string()
        var codeStyleCastSpacingSeverity by string()
        var codeStyleOperatorSpacingSeverity by string()
        var codeStyleCommaSpacingSeverity by string()
        var codeStyleControlStructureSpacingSeverity by string()
        var codeStyleUseStatementSortingSeverity by string()
        var codeStyleFullyQualifiedClassNameSeverity by string()
        var codeStyleFileDocCommentSeverity by string()
    }

}
