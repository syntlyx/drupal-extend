package com.github.nvelychenko.drupalextend.usage

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
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
