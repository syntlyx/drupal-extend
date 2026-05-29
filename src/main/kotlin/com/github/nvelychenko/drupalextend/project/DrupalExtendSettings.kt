package com.github.nvelychenko.drupalextend.project

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

private const val SERVICE_NAME: String = "DrupalExtendSettings"

const val DEFAULT_CONFIG_SYNC_DIRECTORY = "config/sync"

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

    var isCodeStyleDocBlockSummaryPunctuationEnabled: Boolean
        get() = state.isCodeStyleDocBlockSummaryPunctuationEnabled
        set(value) {
            state.isCodeStyleDocBlockSummaryPunctuationEnabled = value
        }

    var isCodeStyleDocBlockBlankLineBeforeTagsEnabled: Boolean
        get() = state.isCodeStyleDocBlockBlankLineBeforeTagsEnabled
        set(value) {
            state.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = value
        }

    var isCodeStyleDocBlockTagGroupSpacingEnabled: Boolean
        get() = state.isCodeStyleDocBlockTagGroupSpacingEnabled
        set(value) {
            state.isCodeStyleDocBlockTagGroupSpacingEnabled = value
        }

    var isCodeStyleDocBlockBlankLineNormalizationEnabled: Boolean
        get() = state.isCodeStyleDocBlockBlankLineNormalizationEnabled
        set(value) {
            state.isCodeStyleDocBlockBlankLineNormalizationEnabled = value
        }

    var isCodeStyleDocBlockContentAfterOpenEnabled: Boolean
        get() = state.isCodeStyleDocBlockContentAfterOpenEnabled
        set(value) {
            state.isCodeStyleDocBlockContentAfterOpenEnabled = value
        }

    var isCodeStyleDocBlockMissingSummaryEnabled: Boolean
        get() = state.isCodeStyleDocBlockMissingSummaryEnabled
        set(value) {
            state.isCodeStyleDocBlockMissingSummaryEnabled = value
        }

    var isCodeStyleDocBlockMissingParamNameEnabled: Boolean
        get() = state.isCodeStyleDocBlockMissingParamNameEnabled
        set(value) {
            state.isCodeStyleDocBlockMissingParamNameEnabled = value
        }

    var isCodeStyleDocBlockTagDescriptionIndentationEnabled: Boolean
        get() = state.isCodeStyleDocBlockTagDescriptionIndentationEnabled
        set(value) {
            state.isCodeStyleDocBlockTagDescriptionIndentationEnabled = value
        }

    var isCodeStyleDocBlockTagDescriptionRequiredEnabled: Boolean
        get() = state.isCodeStyleDocBlockTagDescriptionRequiredEnabled
        set(value) {
            state.isCodeStyleDocBlockTagDescriptionRequiredEnabled = value
        }

    var isCodeStyleDocBlockTagDescriptionLineEnabled: Boolean
        get() = state.isCodeStyleDocBlockTagDescriptionLineEnabled
        set(value) {
            state.isCodeStyleDocBlockTagDescriptionLineEnabled = value
        }

    var isCodeStyleDocBlockTagDescriptionPunctuationEnabled: Boolean
        get() = state.isCodeStyleDocBlockTagDescriptionPunctuationEnabled
        set(value) {
            state.isCodeStyleDocBlockTagDescriptionPunctuationEnabled = value
        }

    var isCodeStyleClassFileNameEnabled: Boolean
        get() = state.isCodeStyleClassFileNameEnabled
        set(value) {
            state.isCodeStyleClassFileNameEnabled = value
        }

    var isCodeStyleClassNameEnabled: Boolean
        get() = state.isCodeStyleClassNameEnabled
        set(value) {
            state.isCodeStyleClassNameEnabled = value
        }

    var isCodeStyleFunctionNameEnabled: Boolean
        get() = state.isCodeStyleFunctionNameEnabled
        set(value) {
            state.isCodeStyleFunctionNameEnabled = value
        }

    var isCodeStyleConstantCasingEnabled: Boolean
        get() = state.isCodeStyleConstantCasingEnabled
        set(value) {
            state.isCodeStyleConstantCasingEnabled = value
        }

    var isCodeStyleClosingPhpTagEnabled: Boolean
        get() = state.isCodeStyleClosingPhpTagEnabled
        set(value) {
            state.isCodeStyleClosingPhpTagEnabled = value
        }

    var isCodeStyleMissingDocCommentsEnabled: Boolean
        get() = state.isCodeStyleMissingDocCommentsEnabled
        set(value) {
            state.isCodeStyleMissingDocCommentsEnabled = value
        }

    var isCodeStyleFunctionSpacingEnabled: Boolean
        get() = state.isCodeStyleFunctionSpacingEnabled
        set(value) {
            state.isCodeStyleFunctionSpacingEnabled = value
        }

    var isCodeStyleClassCloseBraceSpacingEnabled: Boolean
        get() = state.isCodeStyleClassCloseBraceSpacingEnabled
        set(value) {
            state.isCodeStyleClassCloseBraceSpacingEnabled = value
        }

    var isCodeStyleOperatorSpacingEnabled: Boolean
        get() = state.isCodeStyleOperatorSpacingEnabled
        set(value) {
            state.isCodeStyleOperatorSpacingEnabled = value
        }

    var isCodeStyleCommaSpacingEnabled: Boolean
        get() = state.isCodeStyleCommaSpacingEnabled
        set(value) {
            state.isCodeStyleCommaSpacingEnabled = value
        }

    var isCodeStyleUseStatementSortingEnabled: Boolean
        get() = state.isCodeStyleUseStatementSortingEnabled
        set(value) {
            state.isCodeStyleUseStatementSortingEnabled = value
        }

    var configDirectory: String
        get() = state.configDirectory ?: DEFAULT_CONFIG_SYNC_DIRECTORY
        set(value) {
            state.configDirectory = value
        }

    var drupalCoreVersion: String
        get() = state.drupalCoreVersion.orEmpty()
        set(value) {
            state.drupalCoreVersion = value.trim()
        }

    class DrupalExtendState : BaseState() {
        // @todo Once we will implement balloon make it false by default
        var isEnabled: Boolean by property(true)
        var configDirectory by string()
        var drupalCoreVersion by string()
        var isEntityReferenceTypeResolverEnabled: Boolean by property(false)
        var isServicesCompletionEnabled: Boolean by property(true)
        var isRoutesCompletionEnabled: Boolean by property(true)
        var isDependencyInjectionHelperEnabled: Boolean by property(true)
        var isCodeStyleLinterEnabled: Boolean by property(true)
        var isCodeStyleDocBlockSummaryPunctuationEnabled: Boolean by property(true)
        var isCodeStyleDocBlockBlankLineBeforeTagsEnabled: Boolean by property(true)
        var isCodeStyleDocBlockTagGroupSpacingEnabled: Boolean by property(true)
        var isCodeStyleDocBlockBlankLineNormalizationEnabled: Boolean by property(true)
        var isCodeStyleDocBlockContentAfterOpenEnabled: Boolean by property(true)
        var isCodeStyleDocBlockMissingSummaryEnabled: Boolean by property(true)
        var isCodeStyleDocBlockMissingParamNameEnabled: Boolean by property(true)
        var isCodeStyleDocBlockTagDescriptionIndentationEnabled: Boolean by property(true)
        var isCodeStyleDocBlockTagDescriptionRequiredEnabled: Boolean by property(true)
        var isCodeStyleDocBlockTagDescriptionLineEnabled: Boolean by property(true)
        var isCodeStyleDocBlockTagDescriptionPunctuationEnabled: Boolean by property(true)
        var isCodeStyleClassFileNameEnabled: Boolean by property(true)
        var isCodeStyleClassNameEnabled: Boolean by property(true)
        var isCodeStyleFunctionNameEnabled: Boolean by property(true)
        var isCodeStyleConstantCasingEnabled: Boolean by property(true)
        var isCodeStyleClosingPhpTagEnabled: Boolean by property(true)
        var isCodeStyleMissingDocCommentsEnabled: Boolean by property(true)
        var isCodeStyleFunctionSpacingEnabled: Boolean by property(true)
        var isCodeStyleClassCloseBraceSpacingEnabled: Boolean by property(true)
        var isCodeStyleOperatorSpacingEnabled: Boolean by property(true)
        var isCodeStyleCommaSpacingEnabled: Boolean by property(true)
        var isCodeStyleUseStatementSortingEnabled: Boolean by property(true)
    }

}
