package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.inspections.deadcode.PhpUnusedDeclarationInspection
import com.jetbrains.php.lang.inspections.deadcode.local.PhpUnusedSymbolsInspection
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.Function as PhpFunction

class DrupalImplicitUsageProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    fun testModuleHookFunctionAndParametersAreImplicitUsage() {
        val file = myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            /**
             * Implements hook_theme().
             */
            function nll_core_theme(array ${'$'}existing, string ${'$'}type, string ${'$'}theme, string ${'$'}path): array {
              return [];
            }
            """.trimIndent()
        )
        val function = file.phpFunction("nll_core_theme")
        val usageProvider = DrupalImplicitUsageProvider()

        assertTrue(usageProvider.isImplicitUsage(function))
        function.parameters.forEach { parameter ->
            assertTrue(usageProvider.isImplicitUsage(parameter))
            assertTrue(usageProvider.isImplicitRead(parameter))
        }
    }

    fun testInstallHooksAreImplicitUsage() {
        val file = myFixture.configureByText(
            "nll_core.install",
            """
            <?php

            function nll_core_install(): void {}

            function nll_core_update_8124(): void {}

            function nll_core_schema(): array {
              return [];
            }
            """.trimIndent()
        )
        val usageProvider = DrupalImplicitUsageProvider()

        assertTrue(usageProvider.isImplicitUsage(file.phpFunction("nll_core_install")))
        assertTrue(usageProvider.isImplicitUsage(file.phpFunction("nll_core_update_8124")))
        assertTrue(usageProvider.isImplicitUsage(file.phpFunction("nll_core_schema")))
    }

    fun testRegularPhpFunctionsAreNotImplicitUsage() {
        val file = myFixture.configureByText(
            "example.php",
            """
            <?php

            function helper(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )
        val function = file.phpFunction("helper")
        val usageProvider = DrupalImplicitUsageProvider()

        assertFalse(usageProvider.isImplicitUsage(function))
        assertFalse(usageProvider.isImplicitUsage(function.parameters.single()))
        assertFalse(usageProvider.isImplicitRead(function.parameters.single()))
    }

    fun testFieldTypePluginClassIsImplicitUsageWhenFieldStorageConfigUsesId() {
        val file = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldType/LoanDebtSettlementAccountsItem.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\Field\FieldItemBase;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'loan_debt_settlement_accounts',
              label: new TranslatableMarkup('Settled Debts'),
              description: new TranslatableMarkup('Settled Debts Information'),
              default_widget: 'loan_debt_settlement_accounts',
              default_formatter: 'loan_debt_settlement_accounts_default',
            )]
            class LoanDebtSettlementAccountsItem extends FieldItemBase {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/field.storage.node.field_settled_debts.yml",
            """
            id: node.field_settled_debts
            field_name: field_settled_debts
            entity_type: node
            type: loan_debt_settlement_accounts
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("LoanDebtSettlementAccountsItem")))
    }

    fun testFieldWidgetAndFormatterPluginClassesAreImplicitUsageWhenDisplayConfigUsesIds() {
        val widgetFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldWidget/LoanDebtSettlementAccountsWidget.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldWidget(
              id: 'loan_debt_settlement_accounts',
              label: new TranslatableMarkup('Settled Debts'),
              field_types: ['loan_debt_settlement_accounts'],
            )]
            class LoanDebtSettlementAccountsWidget {}
            """.trimIndent()
        )
        val formatterFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldFormatter/LoanDebtSettlementAccountsDefaultFormatter.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'loan_debt_settlement_accounts_default',
              label: new TranslatableMarkup('Settled Debts'),
              field_types: ['loan_debt_settlement_accounts'],
            )]
            class LoanDebtSettlementAccountsDefaultFormatter {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/core.entity_form_display.node.article.default.yml",
            """
            targetEntityType: node
            bundle: article
            mode: default
            content:
              field_settled_debts:
                type: loan_debt_settlement_accounts
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/core.entity_view_display.node.article.default.yml",
            """
            targetEntityType: node
            bundle: article
            mode: default
            content:
              field_settled_debts:
                type: loan_debt_settlement_accounts_default
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(widgetFile.phpClass("LoanDebtSettlementAccountsWidget")))
        assertTrue(usageProvider.isImplicitUsage(formatterFile.phpClass("LoanDebtSettlementAccountsDefaultFormatter")))
    }

    fun testDefaultFieldWidgetAndFormatterAreImplicitUsageWhenFieldTypeIsUsedByConfig() {
        myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldType/LoanDebtSettlementAccountsItem.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'loan_debt_settlement_accounts',
              label: new TranslatableMarkup('Settled Debts'),
              default_widget: 'loan_debt_settlement_accounts',
              default_formatter: 'loan_debt_settlement_accounts_default',
            )]
            class LoanDebtSettlementAccountsItem {}
            """.trimIndent()
        )
        val widgetFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldWidget/LoanDebtSettlementAccountsWidget.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldWidget(
              id: 'loan_debt_settlement_accounts',
              label: new TranslatableMarkup('Settled Debts'),
              field_types: ['loan_debt_settlement_accounts'],
            )]
            class LoanDebtSettlementAccountsWidget {}
            """.trimIndent()
        )
        val formatterFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldFormatter/LoanDebtSettlementAccountsDefaultFormatter.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'loan_debt_settlement_accounts_default',
              label: new TranslatableMarkup('Settled Debts'),
              field_types: ['loan_debt_settlement_accounts'],
            )]
            class LoanDebtSettlementAccountsDefaultFormatter {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/field.storage.node.field_settled_debts.yml",
            """
            id: node.field_settled_debts
            field_name: field_settled_debts
            entity_type: node
            type: loan_debt_settlement_accounts
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(widgetFile.phpClass("LoanDebtSettlementAccountsWidget")))
        assertTrue(usageProvider.isImplicitUsage(formatterFile.phpClass("LoanDebtSettlementAccountsDefaultFormatter")))
    }

    fun testFieldTypePluginClassIsImplicitUsageWithoutAnyConfigOrCodeUsage() {
        val file = myFixture.addFileToProject(
            "modules/custom/institution/src/Plugin/Field/FieldType/InstitutionFeesItem.php",
            """
            <?php

            namespace Drupal\institution\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\Field\FieldItemBase;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'institution_fees',
              label: new TranslatableMarkup('Institution Fees'),
              description: new TranslatableMarkup('Institution Fees settings.'),
              default_widget: 'institution_fees',
              default_formatter: 'institution_fees_default'
            )]
            class InstitutionFeesItem extends FieldItemBase {}
            """.trimIndent()
        )

        // A field type is plugin-discovered and selectable in the Field UI the moment it
        // exists — implicitly used even with no config or code referencing its id.
        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("InstitutionFeesItem")))
    }

    fun testDefaultWidgetAndFormatterOfDefinedFieldTypeAreImplicitUsage() {
        myFixture.addFileToProject(
            "modules/custom/institution/src/Plugin/Field/FieldType/InstitutionFeesItem.php",
            """
            <?php

            namespace Drupal\institution\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'institution_fees',
              label: new TranslatableMarkup('Institution Fees'),
              default_widget: 'institution_fees',
              default_formatter: 'institution_fees_default'
            )]
            class InstitutionFeesItem {}
            """.trimIndent()
        )
        // No field_types on purpose: the compat fallback must not fire, so this pins the
        // "default plugin of a defined (not necessarily used) field type" path.
        val widgetFile = myFixture.addFileToProject(
            "modules/custom/institution/src/Plugin/Field/FieldWidget/InstitutionFeesWidget.php",
            """
            <?php

            namespace Drupal\institution\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldWidget(
              id: 'institution_fees',
              label: new TranslatableMarkup('Institution Fees'),
            )]
            class InstitutionFeesWidget {}
            """.trimIndent()
        )
        val formatterFile = myFixture.addFileToProject(
            "modules/custom/institution/src/Plugin/Field/FieldFormatter/InstitutionFeesFormatter.php",
            """
            <?php

            namespace Drupal\institution\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'institution_fees_default',
              label: new TranslatableMarkup('Institution Fees'),
            )]
            class InstitutionFeesFormatter {}
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(widgetFile.phpClass("InstitutionFeesWidget")))
        assertTrue(usageProvider.isImplicitUsage(formatterFile.phpClass("InstitutionFeesFormatter")))
    }

    fun testFieldWidgetAndFormatterAreImplicitUsageWhenTheirFieldTypeExistsInProject() {
        myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldType/LoanToValueRateItem.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'loan_to_value_rate',
              label: new TranslatableMarkup('Loan To Value Rate'),
            )]
            class LoanToValueRateItem {}
            """.trimIndent()
        )
        val widgetFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldWidget/LoanToValueRateFieldWidget.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldWidget(
              id: 'loan_to_value_rate',
              label: new TranslatableMarkup('Loan To Value Rate'),
              field_types: ['loan_to_value_rate'],
            )]
            class LoanToValueRateFieldWidget extends NLWidgetBase {}
            """.trimIndent()
        )
        val formatterFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldFormatter/LoanToValueRateFormatter.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'loan_to_value_rate_default',
              label: new TranslatableMarkup('Loan To Value Rate'),
              field_types: ['loan_to_value_rate'],
            )]
            class LoanToValueRateFormatter {}
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(widgetFile.phpClass("LoanToValueRateFieldWidget")))
        assertTrue(usageProvider.isImplicitUsage(formatterFile.phpClass("LoanToValueRateFormatter")))
    }

    fun testFieldWidgetIsNotImplicitUsageWhenItsFieldTypeDoesNotExist() {
        val widgetFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldWidget/OrphanWidget.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldWidget(
              id: 'orphan_widget',
              label: new TranslatableMarkup('Orphan'),
              field_types: ['nonexistent_field_type'],
            )]
            class OrphanWidget {}
            """.trimIndent()
        )

        assertFalse(DrupalImplicitUsageProvider().isImplicitUsage(widgetFile.phpClass("OrphanWidget")))
    }

    fun testFieldTypePluginClassIsImplicitUsageWhenFieldInstanceConfigUsesId() {
        val file = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldType/LoanToValueRateItem.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'loan_to_value_rate',
              label: new TranslatableMarkup('Loan To Value Rate'),
            )]
            class LoanToValueRateItem {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/field.field.node.article.field_ltv.yml",
            """
            id: node.article.field_ltv
            field_name: field_ltv
            entity_type: node
            bundle: article
            field_type: loan_to_value_rate
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("LoanToValueRateItem")))
    }

    fun testFieldPluginsAreImplicitUsageWhenBaseFieldDefinitionUsesIds() {
        val typeFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldType/UnderwritingGridItem.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldType;

            use Drupal\Core\Field\Attribute\FieldType;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldType(
              id: 'underwriting_grid',
              label: new TranslatableMarkup('Underwriting Grid'),
            )]
            class UnderwritingGridItem {}
            """.trimIndent()
        )
        val widgetFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldWidget/UnderwritingGridWidget.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldWidget(
              id: 'underwriting_grid_widget',
              label: new TranslatableMarkup('Underwriting Grid'),
              field_types: ['some_other_type'],
            )]
            class UnderwritingGridWidget {}
            """.trimIndent()
        )
        val formatterFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldFormatter/UnderwritingGridFormatter.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'underwriting_grid_formatter',
              label: new TranslatableMarkup('Underwriting Grid'),
              field_types: ['some_other_type'],
            )]
            class UnderwritingGridFormatter {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/loan/src/Entity/Loan.php",
            """
            <?php

            namespace Drupal\loan\Entity;

            use Drupal\Core\Field\BaseFieldDefinition;

            class Loan {
              public static function baseFieldDefinitions(): array {
                ${'$'}fields['underwriting_grid'] = BaseFieldDefinition::create('underwriting_grid')
                  ->setDisplayOptions('form', [
                    'type' => 'underwriting_grid_widget',
                    'weight' => 5,
                  ])
                  ->setDisplayOptions('view', [
                    'label' => 'above',
                    'type' => 'underwriting_grid_formatter',
                  ]);
                return ${'$'}fields;
              }
            }
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(typeFile.phpClass("UnderwritingGridItem")))
        assertTrue(usageProvider.isImplicitUsage(widgetFile.phpClass("UnderwritingGridWidget")))
        assertTrue(usageProvider.isImplicitUsage(formatterFile.phpClass("UnderwritingGridFormatter")))
    }

    fun testFieldFormatterIsImplicitUsageWhenViewsOrLayoutBuilderConfigUsesId() {
        val viewsFormatterFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldFormatter/CreditGradeFormatter.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'credit_grade_formatter',
              label: new TranslatableMarkup('Credit Grade'),
              field_types: ['unknown_type'],
            )]
            class CreditGradeFormatter {}
            """.trimIndent()
        )
        val layoutBuilderFormatterFile = myFixture.addFileToProject(
            "modules/custom/loan/src/Plugin/Field/FieldFormatter/LoanStageFormatter.php",
            """
            <?php

            namespace Drupal\loan\Plugin\Field\FieldFormatter;

            use Drupal\Core\Field\Attribute\FieldFormatter;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[FieldFormatter(
              id: 'loan_stage_formatter',
              label: new TranslatableMarkup('Loan Stage'),
              field_types: ['unknown_type'],
            )]
            class LoanStageFormatter {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/views.view.loans.yml",
            """
            id: loans
            display:
              default:
                display_options:
                  fields:
                    field_credit_grade:
                      id: field_credit_grade
                      type: credit_grade_formatter
                      settings: {  }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/core.entity_view_display.loan.loan.default.yml",
            """
            targetEntityType: loan
            bundle: loan
            mode: default
            third_party_settings:
              layout_builder:
                enabled: true
                sections:
                  - layout_id: layout_onecol
                    components:
                      70a3ad05-b25c-4cf5-a812-337cbe20a34b:
                        configuration:
                          id: 'field_block:loan:loan:loan_stage'
                          formatter:
                            type: loan_stage_formatter
                            settings: {  }
            content: {  }
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(viewsFormatterFile.phpClass("CreditGradeFormatter")))
        assertTrue(usageProvider.isImplicitUsage(layoutBuilderFormatterFile.phpClass("LoanStageFormatter")))
    }

    fun testRestResourcePluginClassIsImplicitUsageWhenConfigEnablesIt() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/rest/resource/ApplicationActionsResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\rest\Attribute\RestResource;

            #[RestResource(
              id: 'nll_core_application_actions',
              label: new TranslatableMarkup('NLL Application Actions.'),
              uri_paths: [
                'canonical' => '/api/application/actions/{method}',
                'create' => '/api/application/actions/{method}',
              ]
            )]
            class ApplicationActionsResource {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/rest.resource.nll_core_application_actions.yml",
            """
            langcode: en
            status: true
            dependencies:
              module:
                - nll_core
                - serialization
            id: nll_core_application_actions
            plugin_id: nll_core_application_actions
            granularity: resource
            configuration:
              methods:
                - GET
                - POST
              formats:
                - json
              authentication:
                - cookie
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("ApplicationActionsResource")))
    }

    fun testRestResourcePluginClassIsNotImplicitUsageWithoutEnablingConfig() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/rest/resource/OrphanResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\rest\Attribute\RestResource;

            #[RestResource(
              id: 'nll_core_orphan',
              label: new TranslatableMarkup('Orphan resource.'),
              uri_paths: [
                'canonical' => '/api/orphan',
              ]
            )]
            class OrphanResource {}
            """.trimIndent()
        )

        // No rest.resource.* config entity enables this plugin id, so the class is
        // genuinely unreachable and keeps its "unused" warning.
        assertFalse(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("OrphanResource")))
    }

    fun testAnnotatedRestResourcePluginClassIsImplicitUsageWhenConfigEnablesIt() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/rest/resource/LegacyActionsResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            /**
             * @RestResource(
             *   id = "nll_core_legacy_actions",
             *   label = @Translation("Legacy Actions."),
             *   uri_paths = {
             *     "canonical" = "/api/legacy/actions"
             *   }
             * )
             */
            class LegacyActionsResource {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/rest.resource.nll_core_legacy_actions.yml",
            """
            id: nll_core_legacy_actions
            plugin_id: nll_core_legacy_actions
            granularity: resource
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("LegacyActionsResource")))
    }

    fun testBlockPluginClassIsImplicitUsageWhenPlacementConfigUsesIt() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Block/HeroBlock.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Block;

            use Drupal\Core\Block\Attribute\Block;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Block(
              id: 'nll_hero_block',
              admin_label: new TranslatableMarkup('Hero'),
            )]
            class HeroBlock {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/block.block.hero.yml",
            """
            id: hero
            theme: olivero
            region: content
            plugin: nll_hero_block
            settings:
              id: nll_hero_block
              label: Hero
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("HeroBlock")))
    }

    fun testDerivedBlockPluginIdInConfigMatchesTheDeriverBaseClass() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Block/NllMenuBlock.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Block;

            use Drupal\Core\Block\Attribute\Block;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Block(
              id: 'nll_menu_block',
              admin_label: new TranslatableMarkup('NLL Menu'),
            )]
            class NllMenuBlock {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/block.block.account_menu.yml",
            """
            id: account_menu
            region: header
            plugin: 'nll_menu_block:account'
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("NllMenuBlock")))
    }

    fun testBlockPluginClassIsNotImplicitUsageWithoutAnyConfig() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Block/OrphanBlock.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Block;

            use Drupal\Core\Block\Attribute\Block;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Block(
              id: 'nll_orphan_block',
              admin_label: new TranslatableMarkup('Orphan'),
            )]
            class OrphanBlock {}
            """.trimIndent()
        )

        assertFalse(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("OrphanBlock")))
    }

    fun testBlockAndLayoutPluginsAreImplicitUsageWhenLayoutBuilderSectionUsesThem() {
        val blockFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Block/PromoBlock.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Block;

            use Drupal\Core\Block\Attribute\Block;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Block(
              id: 'nll_promo_block',
              admin_label: new TranslatableMarkup('Promo'),
            )]
            class PromoBlock {}
            """.trimIndent()
        )
        val layoutFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Layout/TwoColumnLayout.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Layout;

            use Drupal\Core\Layout\Attribute\Layout;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Layout(
              id: 'nll_two_column',
              label: new TranslatableMarkup('Two column'),
            )]
            class TwoColumnLayout {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/core.entity_view_display.node.landing.default.yml",
            """
            targetEntityType: node
            bundle: landing
            mode: default
            third_party_settings:
              layout_builder:
                enabled: true
                sections:
                  - layout_id: nll_two_column
                    components:
                      70a3ad05-b25c-4cf5-a812-337cbe20a34b:
                        configuration:
                          id: nll_promo_block
                          label_display: '0'
            content: {  }
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(blockFile.phpClass("PromoBlock")))
        assertTrue(usageProvider.isImplicitUsage(layoutFile.phpClass("TwoColumnLayout")))
    }

    fun testActionImageEffectMediaSourceAndFilterPluginsAreImplicitUsageViaTheirConfigEntities() {
        val actionFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Action/ArchiveLoan.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Action;

            use Drupal\Core\Action\Attribute\Action;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Action(
              id: 'nll_archive_loan',
              label: new TranslatableMarkup('Archive loan'),
            )]
            class ArchiveLoan {}
            """.trimIndent()
        )
        val effectFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/ImageEffect/WatermarkEffect.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\ImageEffect;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\image\Attribute\ImageEffect;

            #[ImageEffect(
              id: 'nll_watermark',
              label: new TranslatableMarkup('Watermark'),
            )]
            class WatermarkEffect {}
            """.trimIndent()
        )
        val sourceFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/media/Source/StatementSource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\media\Source;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\media\Attribute\MediaSource;

            #[MediaSource(
              id: 'nll_statement_source',
              label: new TranslatableMarkup('Statement'),
            )]
            class StatementSource {}
            """.trimIndent()
        )
        val filterFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Filter/RedactPiiFilter.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Filter;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\filter\Attribute\Filter;

            #[Filter(
              id: 'nll_redact_pii',
              title: new TranslatableMarkup('Redact PII'),
            )]
            class RedactPiiFilter {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/system.action.loan_archive.yml",
            """
            id: loan_archive
            label: 'Archive loans'
            type: loan
            plugin: nll_archive_loan
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/image.style.thumbnail_watermark.yml",
            """
            name: thumbnail_watermark
            label: 'Thumbnail watermark'
            effects:
              b7c2a940-ab00-4a03-b6cc-27b14b3c1e40:
                uuid: b7c2a940-ab00-4a03-b6cc-27b14b3c1e40
                id: nll_watermark
                weight: 1
                data:
                  opacity: 50
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/media.type.statement.yml",
            """
            id: statement
            label: Statement
            source: nll_statement_source
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/filter.format.compliance_html.yml",
            """
            format: compliance_html
            name: 'Compliance HTML'
            filters:
              nll_redact_pii:
                id: nll_redact_pii
                provider: nll_core
                status: true
                weight: 0
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(actionFile.phpClass("ArchiveLoan")))
        assertTrue(usageProvider.isImplicitUsage(effectFile.phpClass("WatermarkEffect")))
        assertTrue(usageProvider.isImplicitUsage(sourceFile.phpClass("StatementSource")))
        assertTrue(usageProvider.isImplicitUsage(filterFile.phpClass("RedactPiiFilter")))
    }

    fun testViewsPluginClassesAreImplicitUsageWhenViewsConfigUsesThem() {
        val fieldFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/views/field/LoanStatusField.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\field;

            use Drupal\views\Attribute\ViewsField;

            #[ViewsField("nll_loan_status")]
            class LoanStatusField {}
            """.trimIndent()
        )
        val filterFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/views/filter/LoanStageFilter.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\filter;

            use Drupal\views\Attribute\ViewsFilter;

            #[ViewsFilter("nll_loan_stage")]
            class LoanStageFilter {}
            """.trimIndent()
        )
        val styleFile = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/views/style/LoanTableStyle.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\style;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\views\Attribute\ViewsStyle;

            #[ViewsStyle(
              id: 'nll_loan_table',
              title: new TranslatableMarkup('Loan table'),
            )]
            class LoanTableStyle {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/views.view.loan_pipeline.yml",
            """
            id: loan_pipeline
            display:
              default:
                display_plugin: default
                position: 0
                display_options:
                  style:
                    type: nll_loan_table
                  fields:
                    loan_status:
                      id: loan_status
                      table: loan
                      field: status
                      plugin_id: nll_loan_status
                  filters:
                    stage:
                      id: stage
                      plugin_id: nll_loan_stage
            """.trimIndent()
        )

        val usageProvider = DrupalImplicitUsageProvider()
        assertTrue(usageProvider.isImplicitUsage(fieldFile.phpClass("LoanStatusField")))
        assertTrue(usageProvider.isImplicitUsage(filterFile.phpClass("LoanStageFilter")))
        assertTrue(usageProvider.isImplicitUsage(styleFile.phpClass("LoanTableStyle")))
    }

    fun testLegacyViewsFieldAnnotationWithPositionalIdIsImplicitUsage() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/views/field/LegacyAmountField.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\field;

            /**
             * Renders the loan amount.
             *
             * @ViewsField("nll_legacy_amount")
             */
            class LegacyAmountField {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/views.view.legacy_loans.yml",
            """
            id: legacy_loans
            display:
              default:
                display_plugin: default
                display_options:
                  fields:
                    amount:
                      id: amount
                      plugin_id: nll_legacy_amount
            """.trimIndent()
        )

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(file.phpClass("LegacyAmountField")))
    }

    fun testFactoryMethodIsImplicitUsageViaInlineClassFactory() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/ConsumerFactory.php",
            """
            <?php

            namespace Drupal\my_module;

            class ConsumerFactory {
              public static function create() {}

              public function unrelated() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              sample.consumer:
                class: Drupal\my_module\Consumer
                factory: Drupal\my_module\ConsumerFactory::create
            """.trimIndent()
        )

        val phpClass = classFile.phpClass("ConsumerFactory")
        val usageProvider = DrupalImplicitUsageProvider()

        val createMethod = phpClass.findMethodByName("create") ?: error("Expected create method")
        val unrelatedMethod = phpClass.findMethodByName("unrelated") ?: error("Expected unrelated method")

        assertTrue(usageProvider.isImplicitUsage(createMethod))
        assertFalse(usageProvider.isImplicitUsage(unrelatedMethod))
    }

    fun testFactoryMethodIsImplicitUsageViaServiceReferenceFactory() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/ClientFactory.php",
            """
            <?php

            namespace Drupal\my_module;

            class ClientFactory {
              public function dynamoDbClient() {}

              public function unrelated() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              aws.client_factory:
                class: Drupal\my_module\ClientFactory
              aws.dynamodb_client:
                class: Aws\DynamoDb\DynamoDbClient
                factory: ['@aws.client_factory', 'dynamoDbClient']
            """.trimIndent()
        )

        val phpClass = classFile.phpClass("ClientFactory")
        val usageProvider = DrupalImplicitUsageProvider()

        val factoryMethod = phpClass.findMethodByName("dynamoDbClient") ?: error("Expected factory method")
        val unrelatedMethod = phpClass.findMethodByName("unrelated") ?: error("Expected unrelated method")

        assertTrue(usageProvider.isImplicitUsage(factoryMethod))
        assertFalse(usageProvider.isImplicitUsage(unrelatedMethod))
    }

    fun testHookAttributeMethodsAndTheirClassAreImplicitUsage() {
        addHookAttributeClasses()
        val file = myFixture.configureByText(
            "MyModuleHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;
            use Drupal\Core\Hook\Attribute\RemoveHook;
            use Drupal\Core\Hook\Attribute\ReorderHook;

            class MyModuleHooks {

              #[Hook('node_view')]
              public function nodeView(): void {}

              #[ReorderHook('node_view')]
              public function reorder(): void {}

              #[RemoveHook('node_view')]
              public function remove(): void {}

              public function helper(): void {}
            }
            """.trimIndent()
        )
        val phpClass = file.phpClass("MyModuleHooks")
        val usageProvider = DrupalImplicitUsageProvider()

        assertTrue(usageProvider.isImplicitUsage(phpClass.findMethodByName("nodeView")!!))
        assertTrue(usageProvider.isImplicitUsage(phpClass.findMethodByName("reorder")!!))
        assertTrue(usageProvider.isImplicitUsage(phpClass.findMethodByName("remove")!!))
        assertFalse(usageProvider.isImplicitUsage(phpClass.findMethodByName("helper")!!))
        assertTrue("Class hosting hook methods must be implicitly used", usageProvider.isImplicitUsage(phpClass))
    }

    fun testLegacyHookFunctionIsImplicitUsage() {
        addHookAttributeClasses()
        // A .php file and a name outside the modulename_hookname convention: only the
        // attribute marks this function as a BC shim invoked by the hook system.
        val file = myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            use Drupal\Core\Hook\Attribute\LegacyHook;

            #[LegacyHook]
            function my_custom_legacy_shim(): void {}

            function plain_helper(): void {}
            """.trimIndent()
        )
        val usageProvider = DrupalImplicitUsageProvider()

        assertTrue(usageProvider.isImplicitUsage(file.phpFunction("my_custom_legacy_shim")))
        assertFalse(usageProvider.isImplicitUsage(file.phpFunction("plain_helper")))
    }

    /**
     * End-to-end through PhpStorm's dead-code pass ("Unused element: …"), not a direct
     * provider call — pins the whole chain: inspection → ImplicitUsageProvider EP →
     * plugin-config-usage index → config YAML.
     */
    fun testRestResourceClassIsNotHighlightedUnusedEndToEnd() {
        myFixture.enableInspections(PhpUnusedDeclarationInspection(), PhpUnusedSymbolsInspection())
        myFixture.addFileToProject(
            "config/sync/rest.resource.nll_core_application_actions.yml",
            """
            id: nll_core_application_actions
            plugin_id: nll_core_application_actions
            granularity: resource
            """.trimIndent()
        )
        myFixture.configureByText(
            "ApplicationActionsResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\rest\Attribute\RestResource;

            #[RestResource(
              id: 'nll_core_application_actions',
              label: new TranslatableMarkup('NLL Application Actions.'),
            )]
            class ApplicationActionsResource {}
            """.trimIndent()
        )

        val unusedHighlights = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Unused element") == true }

        assertEmpty(unusedHighlights)
    }

    fun testOrphanRestResourceClassIsHighlightedUnusedEndToEnd() {
        myFixture.enableInspections(PhpUnusedDeclarationInspection(), PhpUnusedSymbolsInspection())
        // Negative control: proves the dead-code pass actually runs in this fixture, so the
        // empty-highlight assertion above can't pass vacuously.
        myFixture.configureByText(
            "OrphanResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            use Drupal\rest\Attribute\RestResource;

            #[RestResource(
              id: 'nll_core_orphan',
            )]
            class OrphanResource {}
            """.trimIndent()
        )

        val unusedHighlights = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Unused element") == true }

        assertSize(1, unusedHighlights)
    }

    fun testViewsFieldClassIsNotHighlightedUnusedEndToEnd() {
        myFixture.enableInspections(PhpUnusedDeclarationInspection(), PhpUnusedSymbolsInspection())
        myFixture.addFileToProject(
            "config/sync/views.view.loan_pipeline.yml",
            """
            id: loan_pipeline
            display:
              default:
                display_plugin: default
                display_options:
                  fields:
                    loan_status:
                      id: loan_status
                      plugin_id: nll_loan_status
            """.trimIndent()
        )
        myFixture.configureByText(
            "LoanStatusField.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\field;

            use Drupal\views\Attribute\ViewsField;

            #[ViewsField("nll_loan_status")]
            class LoanStatusField {}
            """.trimIndent()
        )

        val unusedHighlights = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Unused element") == true }

        assertEmpty(unusedHighlights)
    }

    /**
     * Real-world layout: the config-sync directory (`config/default`, BLT-style) is EXCLUDED
     * from the project. Excluded folders are invisible to file-based indexes, so without the
     * [com.github.nvelychenko.drupalextend.project.DrupalConfigLibraryRootsProvider] synthetic
     * library the plugin ids enabled there would never be found and every plugin class would
     * be flagged unused.
     */
    fun testPluginsEnabledInExcludedConfigDirectoryAreStillImplicitUsage() {
        myFixture.addFileToProject(
            "config/default/core.extension.yml",
            """
            module:
              rest: 0
              views: 0
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/default/rest.resource.nll_core_verify_address.yml",
            """
            id: nll_core_verify_address
            plugin_id: nll_core_verify_address
            granularity: resource
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/default/views.view.applications.yml",
            """
            id: applications
            display:
              default:
                display_plugin: default
                display_options:
                  fields:
                    loan_funded_amount:
                      id: loan_funded_amount
                      plugin_id: node_loan_funded_amount
                  filters:
                    pre_approved_date:
                      id: pre_approved_date
                      plugin_id: pre_approved_date_filter
            """.trimIndent()
        )
        val restFile = myFixture.addFileToProject(
            "docroot/modules/custom/nll_core/src/Plugin/rest/resource/AddressResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            use Drupal\Core\StringTranslation\TranslatableMarkup;
            use Drupal\rest\Attribute\RestResource;

            #[RestResource(
              id: 'nll_core_verify_address',
              label: new TranslatableMarkup('NLL Verify address'),
            )]
            class AddressResource {}
            """.trimIndent()
        )
        val viewsFieldFile = myFixture.addFileToProject(
            "docroot/modules/custom/nll_core/src/Plugin/views/field/ApplicationFundedAmount.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\field;

            use Drupal\views\Attribute\ViewsField;

            #[ViewsField(
              'node_loan_funded_amount',
            )]
            class ApplicationFundedAmount {}
            """.trimIndent()
        )
        val viewsFilterFile = myFixture.addFileToProject(
            "docroot/modules/custom/nll_core/src/Plugin/views/filter/PreApprovedDateFilter.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\filter;

            use Drupal\views\Attribute\ViewsFilter;

            #[ViewsFilter(
              'pre_approved_date_filter',
            )]
            class PreApprovedDateFilter {}
            """.trimIndent()
        )

        val configDir = myFixture.findFileInTempDir("config")
        PsiTestUtil.addExcludedRoot(myFixture.module, configDir)
        try {
            val usageProvider = DrupalImplicitUsageProvider()
            assertTrue(usageProvider.isImplicitUsage(restFile.phpClass("AddressResource")))
            assertTrue(usageProvider.isImplicitUsage(viewsFieldFile.phpClass("ApplicationFundedAmount")))
            assertTrue(usageProvider.isImplicitUsage(viewsFilterFile.phpClass("PreApprovedDateFilter")))
        } finally {
            PsiTestUtil.removeExcludedRoot(myFixture.module, configDir)
        }
    }

    /**
     * Negative control for the excluded-directory test: without the `core.extension.yml`
     * marker the directory is not recognized as a config-sync library, so exclusion really
     * does hide it from the index — proving the positive test doesn't pass vacuously.
     */
    fun testPluginsEnabledInExcludedUnmarkedDirectoryAreNotImplicitUsage() {
        myFixture.addFileToProject(
            "config/default/rest.resource.nll_core_orphaned.yml",
            """
            id: nll_core_orphaned
            plugin_id: nll_core_orphaned
            """.trimIndent()
        )
        val restFile = myFixture.addFileToProject(
            "docroot/modules/custom/nll_core/src/Plugin/rest/resource/OrphanedResource.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\rest\resource;

            use Drupal\rest\Attribute\RestResource;

            #[RestResource(
              id: 'nll_core_orphaned',
            )]
            class OrphanedResource {}
            """.trimIndent()
        )

        val configDir = myFixture.findFileInTempDir("config")
        PsiTestUtil.addExcludedRoot(myFixture.module, configDir)
        try {
            assertFalse(DrupalImplicitUsageProvider().isImplicitUsage(restFile.phpClass("OrphanedResource")))
        } finally {
            PsiTestUtil.removeExcludedRoot(myFixture.module, configDir)
        }
    }

    private fun addHookAttributeClasses() {
        for (name in listOf("Hook", "LegacyHook", "ReorderHook", "RemoveHook")) {
            myFixture.addFileToProject(
                "core/lib/Drupal/Core/Hook/Attribute/$name.php",
                $$"""
                <?php

                namespace Drupal\Core\Hook\Attribute;

                #[\Attribute]
                class $$name {
                  public function __construct(...$arguments) {}
                }
                """.trimIndent()
            )
        }
    }

    private fun PsiFile.phpFunction(name: String): PhpFunction {
        return PsiTreeUtil.findChildrenOfType(this, PhpFunction::class.java)
            .firstOrNull { it.name == name }
            ?: error("Expected PHP function $name")
    }

    private fun PsiFile.phpClass(name: String): PhpClass {
        return PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .firstOrNull { it.name == name }
            ?: error("Expected PHP class $name")
    }
}
