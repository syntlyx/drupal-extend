package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalPluginAttributeInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    fun testQuickFixConvertsFieldWidgetAnnotationToAttributeForDrupal103() {
        project.drupalExtendSettings.drupalCoreVersion = "10.3"
        myFixture.enableInspections(DrupalPluginAttributeInspection::class.java)
        myFixture.configureByText(
            "VehicleInfoWidget.php",
            """
            <?php

            namespace Drupal\my_module\Plugin\Field\FieldWidget;

            /**
             * Plugin implementation of the vehicle_info widget.
             *
             * @FieldWidget(
             *   id = "vehicle_info",
             *   label = @Translation("Vehicle Information"),
             *   field_types = {
             *     "vehicle_info",
             *   },
             * )
             */
            class VehicleInfoWidget {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Convert @FieldWidget annotation to PHP attribute"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module\Plugin\Field\FieldWidget;

            use Drupal\Core\Field\Attribute\FieldWidget;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            /**
             * Plugin implementation of the vehicle_info widget.
             */
            #[FieldWidget(
              id: 'vehicle_info',
              label: new TranslatableMarkup('Vehicle Information'),
              field_types: ['vehicle_info'],
            )]
            class VehicleInfoWidget {}
            """.trimIndent()
        )
    }

    fun testInspectionSkipsFieldWidgetAnnotationBeforeDrupal103() {
        project.drupalExtendSettings.drupalCoreVersion = "10.2"
        myFixture.enableInspections(DrupalPluginAttributeInspection::class.java)
        myFixture.configureByText(
            "VehicleInfoWidget.php",
            """
            <?php

            /**
             * @FieldWidget(
             *   id = "vehicle_info",
             *   label = @Translation("Vehicle Information"),
             *   field_types = {
             *     "vehicle_info",
             *   },
             * )
             */
            class VehicleInfoWidget {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("FieldWidget annotation can be converted to a PHP attribute"))
    }

    fun testQuickFixConvertsBlockAnnotationWithContextDefinition() {
        project.drupalExtendSettings.drupalCoreVersion = "10.3"
        myFixture.enableInspections(DrupalPluginAttributeInspection::class.java)
        myFixture.configureByText(
            "VehicleBlock.php",
            """
            <?php

            namespace Drupal\my_module\Plugin\Block;

            /**
             * Provides a vehicle block.
             *
             * @Block(
             *   id = "vehicle_block",
             *   admin_label = @Translation("Vehicle block"),
             *   category = @Translation("Vehicles"),
             *   context_definitions = {
             *     "node" = @ContextDefinition("entity:node", label = @Translation("Node"), required = FALSE)
             *   }
             * )
             */
            final class VehicleBlock {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Convert @Block annotation to PHP attribute"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module\Plugin\Block;

            use Drupal\Core\Block\Attribute\Block;
            use Drupal\Core\Plugin\Context\ContextDefinition;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            /**
             * Provides a vehicle block.
             */
            #[Block(
              id: 'vehicle_block',
              admin_label: new TranslatableMarkup('Vehicle block'),
              category: new TranslatableMarkup('Vehicles'),
              context_definitions: [
                'node' => new ContextDefinition(
                  'entity:node',
                  label: new TranslatableMarkup('Node'),
                  required: false,
                ),
              ],
            )]
            final class VehicleBlock {}
            """.trimIndent()
        )
    }

    fun testQuickFixConvertsViewsFieldPositionalAnnotation() {
        project.drupalExtendSettings.drupalCoreVersion = "10.3"
        myFixture.enableInspections(DrupalPluginAttributeInspection::class.java)
        myFixture.configureByText(
            "VehicleViewsField.php",
            """
            <?php

            namespace Drupal\my_module\Plugin\views\field;

            /**
             * @ViewsField("vehicle_info")
             */
            final class VehicleViewsField {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Convert @ViewsField annotation to PHP attribute"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module\Plugin\views\field;

            use Drupal\views\Attribute\ViewsField;

            #[ViewsField(
              'vehicle_info',
            )]
            final class VehicleViewsField {}
            """.trimIndent()
        )
    }

    fun testInspectionSkipsFieldWidgetAnnotationWithUnsupportedArguments() {
        project.drupalExtendSettings.drupalCoreVersion = "10.3"
        myFixture.enableInspections(DrupalPluginAttributeInspection::class.java)
        myFixture.configureByText(
            "VehicleInfoWidget.php",
            """
            <?php

            /**
             * @FieldWidget(
             *   id = "vehicle_info",
             *   label = @Translation("Vehicle Information"),
             *   custom = "keep_me",
             * )
             */
            class VehicleInfoWidget {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("FieldWidget annotation can be converted to a PHP attribute"))
    }
}
