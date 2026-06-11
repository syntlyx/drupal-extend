package com.github.nvelychenko.drupalextend.project.configurable

import com.github.nvelychenko.drupalextend.DrupalExtendBundle
import com.github.nvelychenko.drupalextend.project.DrupalCodeStyleSeverity
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.clearPluginIndexes
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*

class DrupalConfig(
    val project: Project,
) : BoundConfigurable(DrupalExtendBundle.message("settings.drupal_extend.title")) {
    private val settings = project.drupalExtendSettings

    override fun createPanel(): DialogPanel = panel {
        group(DrupalExtendBundle.message("settings.title")) {
            lateinit var isEnabledCheckbox: Cell<JBCheckBox>
            row {
                // No clearPluginIndexes() on apply: indexers no longer read settings, so the
                // indexed data is the same whether the plugin is enabled or not — toggling
                // only changes whether consumers use it.
                isEnabledCheckbox = checkBox(DrupalExtendBundle.message("settings.drupal_extend.is_enabled"))
                    .bindSelected(settings::isEnabled)

                button(DrupalExtendBundle.message("settings.drupal_extend.clear_index")) { clearPluginIndexes() }
                    .align(AlignX.RIGHT)
            }

            row(DrupalExtendBundle.message("settings.drupal_extend.drupal_core_version")) {
                textField()
                    .enabledIf(isEnabledCheckbox.selected)
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.drupal_core_version_description"))
                    .resizableColumn()
                    .align(Align.FILL)
                    .bindText(settings::drupalCoreVersion)
            }

            row {
                checkBox(DrupalExtendBundle.message("settings.drupal_extend.services_completion"))
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.services_completion_description"))
                    .bindSelected(settings::isServicesCompletionEnabled)
                    .enabledIf(isEnabledCheckbox.selected)
            }

            row {
                checkBox(DrupalExtendBundle.message("settings.drupal_extend.routes_completion"))
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.routes_completion_description"))
                    .bindSelected(settings::isRoutesCompletionEnabled)
                    .enabledIf(isEnabledCheckbox.selected)
            }

            row {
                checkBox(DrupalExtendBundle.message("settings.drupal_extend.dependency_injection_helper"))
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.dependency_injection_helper_description"))
                    .bindSelected(settings::isDependencyInjectionHelperEnabled)
                    .enabledIf(isEnabledCheckbox.selected)
            }

            lateinit var codeStyleLinterCheckbox: Cell<JBCheckBox>
            row {
                codeStyleLinterCheckbox = checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_linter"))
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.code_style_linter_description"))
                    .bindSelected(settings::isCodeStyleLinterEnabled)
                    .enabledIf(isEnabledCheckbox.selected)
            }

            indent {
                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_naming"), true) {
                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_class_file_name")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleClassFileNameSeverity }, { settings.codeStyleClassFileNameSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_class_name")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleClassNameSeverity }, { settings.codeStyleClassNameSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_function_name")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFunctionNameSeverity }, { settings.codeStyleFunctionNameSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_member_visibility")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleMemberVisibilitySeverity }, { settings.codeStyleMemberVisibilitySeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_property_name")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStylePropertyNameSeverity }, { settings.codeStylePropertyNameSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_php_syntax"), true) {
                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_constant_casing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleConstantCasingSeverity }, { settings.codeStyleConstantCasingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_closing_php_tag")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleClosingPhpTagSeverity }, { settings.codeStyleClosingPhpTagSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_file_whitespace")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFileWhitespaceSeverity }, { settings.codeStyleFileWhitespaceSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_short_array_syntax")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleShortArraySyntaxSeverity }, { settings.codeStyleShortArraySyntaxSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_multiline_array_style")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleMultilineArrayStyleSeverity }, { settings.codeStyleMultilineArrayStyleSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_elseif_syntax")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleElseIfSyntaxSeverity }, { settings.codeStyleElseIfSyntaxSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_else_placement")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleElsePlacementSeverity }, { settings.codeStyleElsePlacementSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_string_quotes")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleStringQuotesSeverity }, { settings.codeStyleStringQuotesSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_inline_comment")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleInlineCommentSeverity }, { settings.codeStyleInlineCommentSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_discouraged_functions")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDiscouragedFunctionsSeverity }, { settings.codeStyleDiscouragedFunctionsSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_consecutive_blank_lines")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleConsecutiveBlankLinesSeverity }, { settings.codeStyleConsecutiveBlankLinesSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_imports"), true) {
                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_use_statement_sorting")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleUseStatementSortingSeverity }, { settings.codeStyleUseStatementSortingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_fully_qualified_class_name")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFullyQualifiedClassNameSeverity }, { settings.codeStyleFullyQualifiedClassNameSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_spacing"), true) {
                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_function_declaration_style")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFunctionDeclarationStyleSeverity }, { settings.codeStyleFunctionDeclarationStyleSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_function_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFunctionSpacingSeverity }, { settings.codeStyleFunctionSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_class_close_brace_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleClassCloseBraceSpacingSeverity }, { settings.codeStyleClassCloseBraceSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_block_blank_lines")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleBlockBlankLinesSeverity }, { settings.codeStyleBlockBlankLinesSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_cast_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleCastSpacingSeverity }, { settings.codeStyleCastSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_operator_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleOperatorSpacingSeverity }, { settings.codeStyleOperatorSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_comma_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleCommaSpacingSeverity }, { settings.codeStyleCommaSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_control_structure_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleControlStructureSpacingSeverity }, { settings.codeStyleControlStructureSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_scope_keyword_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleScopeKeywordSpacingSeverity }, { settings.codeStyleScopeKeywordSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_docblock"), true) {
                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_missing_doc_comments")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleMissingDocCommentsSeverity }, { settings.codeStyleMissingDocCommentsSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_summary_punctuation")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockSummaryPunctuationSeverity }, { settings.codeStyleDocBlockSummaryPunctuationSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_blank_line_before_tags")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockBlankLineBeforeTagsSeverity }, { settings.codeStyleDocBlockBlankLineBeforeTagsSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_group_spacing")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockTagGroupSpacingSeverity }, { settings.codeStyleDocBlockTagGroupSpacingSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_blank_line_normalization")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockBlankLineNormalizationSeverity }, { settings.codeStyleDocBlockBlankLineNormalizationSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_content_after_open")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockContentAfterOpenSeverity }, { settings.codeStyleDocBlockContentAfterOpenSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_missing_summary")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockMissingSummarySeverity }, { settings.codeStyleDocBlockMissingSummarySeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_missing_param_name")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockMissingParamNameSeverity }, { settings.codeStyleDocBlockMissingParamNameSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_indentation")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockTagDescriptionIndentationSeverity }, { settings.codeStyleDocBlockTagDescriptionIndentationSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_required")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockTagDescriptionRequiredSeverity }, { settings.codeStyleDocBlockTagDescriptionRequiredSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_line")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockTagDescriptionLineSeverity }, { settings.codeStyleDocBlockTagDescriptionLineSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_punctuation")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockTagDescriptionPunctuationSeverity }, { settings.codeStyleDocBlockTagDescriptionPunctuationSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_api_tags")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockApiTagsSeverity }, { settings.codeStyleDocBlockApiTagsSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_doc_block_short_types")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockShortTypesSeverity }, { settings.codeStyleDocBlockShortTypesSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_doc_block_summary_length")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleDocBlockSummaryLengthSeverity }, { settings.codeStyleDocBlockSummaryLengthSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_function_doc_missing_tags")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFunctionDocMissingTagsSeverity }, { settings.codeStyleFunctionDocMissingTagsSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row(DrupalExtendBundle.message("settings.drupal_extend.code_style_file_doc_comment")) {
                        comboBox(DrupalCodeStyleSeverity.entries)
                            .bindItem({ settings.codeStyleFileDocCommentSeverity }, { settings.codeStyleFileDocCommentSeverity = it ?: DrupalCodeStyleSeverity.OFF })
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }
            }

            row {
                checkBox(DrupalExtendBundle.message("settings.drupal_extend.experimental_entity_reference_type_provider"))
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.experimental_entity_reference_type_provider_description"))
                    .bindSelected(settings::isEntityReferenceTypeResolverEnabled)
            }
        }
    }

}
