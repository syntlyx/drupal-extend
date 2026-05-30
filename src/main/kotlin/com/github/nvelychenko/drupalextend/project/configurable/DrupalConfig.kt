package com.github.nvelychenko.drupalextend.project.configurable

import com.github.nvelychenko.drupalextend.DrupalExtendBundle
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.clearPluginIndexes
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
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
                isEnabledCheckbox = checkBox(DrupalExtendBundle.message("settings.drupal_extend.is_enabled"))
                    .bindSelected(settings::isEnabled)
                    .onApply { clearPluginIndexes() }
            }

            row {
                val fileChooserDescriptor =
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withFileFilter { it.isDirectory }
                val configDirectoryField = TextFieldWithBrowseButton().apply {
                    addBrowseFolderListener(project, fileChooserDescriptor)
                }

                cell(configDirectoryField)
                    .enabledIf(isEnabledCheckbox.selected)
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.config_directory.title"))
                    .resizableColumn()
                    .align(Align.FILL)
                    .bindText(settings::configDirectory)
                    .onApply {
                        clearPluginIndexes()
                    }

                button(DrupalExtendBundle.message("settings.drupal_extend.clear_index")) { clearPluginIndexes() }
                    .enabledIf(isEnabledCheckbox.selected)
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
                    .onApply { clearPluginIndexes() }
            }

            row {
                checkBox(DrupalExtendBundle.message("settings.drupal_extend.routes_completion"))
                    .comment(DrupalExtendBundle.message("settings.drupal_extend.routes_completion_description"))
                    .bindSelected(settings::isRoutesCompletionEnabled)
                    .enabledIf(isEnabledCheckbox.selected)
                    .onApply { clearPluginIndexes() }
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
                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_class_file_name"))
                            .bindSelected(settings::isCodeStyleClassFileNameEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_class_name"))
                            .bindSelected(settings::isCodeStyleClassNameEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_function_name"))
                            .bindSelected(settings::isCodeStyleFunctionNameEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_php_syntax"), true) {
                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_constant_casing"))
                            .bindSelected(settings::isCodeStyleConstantCasingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_closing_php_tag"))
                            .bindSelected(settings::isCodeStyleClosingPhpTagEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_short_array_syntax"))
                            .bindSelected(settings::isCodeStyleShortArraySyntaxEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_elseif_syntax"))
                            .bindSelected(settings::isCodeStyleElseIfSyntaxEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_imports"), true) {
                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_use_statement_sorting"))
                            .bindSelected(settings::isCodeStyleUseStatementSortingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_fully_qualified_class_name"))
                            .bindSelected(settings::isCodeStyleFullyQualifiedClassNameEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_spacing"), true) {
                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_function_spacing"))
                            .bindSelected(settings::isCodeStyleFunctionSpacingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_class_close_brace_spacing"))
                            .bindSelected(settings::isCodeStyleClassCloseBraceSpacingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_operator_spacing"))
                            .bindSelected(settings::isCodeStyleOperatorSpacingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_comma_spacing"))
                            .bindSelected(settings::isCodeStyleCommaSpacingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_control_structure_spacing"))
                            .bindSelected(settings::isCodeStyleControlStructureSpacingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }
                }

                collapsibleGroup(DrupalExtendBundle.message("settings.drupal_extend.code_style_group_docblock"), true) {
                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_missing_doc_comments"))
                            .bindSelected(settings::isCodeStyleMissingDocCommentsEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_summary_punctuation"))
                            .bindSelected(settings::isCodeStyleDocBlockSummaryPunctuationEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_blank_line_before_tags"))
                            .bindSelected(settings::isCodeStyleDocBlockBlankLineBeforeTagsEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_group_spacing"))
                            .bindSelected(settings::isCodeStyleDocBlockTagGroupSpacingEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_blank_line_normalization"))
                            .bindSelected(settings::isCodeStyleDocBlockBlankLineNormalizationEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_content_after_open"))
                            .bindSelected(settings::isCodeStyleDocBlockContentAfterOpenEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_missing_summary"))
                            .bindSelected(settings::isCodeStyleDocBlockMissingSummaryEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_missing_param_name"))
                            .bindSelected(settings::isCodeStyleDocBlockMissingParamNameEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_indentation"))
                            .bindSelected(settings::isCodeStyleDocBlockTagDescriptionIndentationEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_required"))
                            .bindSelected(settings::isCodeStyleDocBlockTagDescriptionRequiredEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_line"))
                            .bindSelected(settings::isCodeStyleDocBlockTagDescriptionLineEnabled)
                            .enabledIf(codeStyleLinterCheckbox.selected)
                    }

                    row {
                        checkBox(DrupalExtendBundle.message("settings.drupal_extend.code_style_docblock_tag_description_punctuation"))
                            .bindSelected(settings::isCodeStyleDocBlockTagDescriptionPunctuationEnabled)
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
