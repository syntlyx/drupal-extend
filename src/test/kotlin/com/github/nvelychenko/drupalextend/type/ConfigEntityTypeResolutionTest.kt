package com.github.nvelychenko.drupalextend.type

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.MethodReference

class ConfigEntityTypeResolutionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        addDrupalStubs()
        addConfigEntity()
    }

    fun testGetStorageResolvesToConfigEntityStorageInterfaces() {
        val reference = configureAndFindReference(
            $$"""
            <?php

            namespace Drupal\my_module;

            class Example {
              public function build(): void {
                $storage = \Drupal::entityTypeManager()->getStor<caret>age('view');
              }
            }
            """.trimIndent()
        )

        val types = reference.type.global(project).types
        assertContainsElements(types, "\\Drupal\\Core\\Config\\Entity\\ConfigEntityStorageInterface")
    }

    fun testLoadOnConfigStorageResolvesToConfigEntityClass() {
        val reference = configureAndFindReference(
            $$"""
            <?php

            namespace Drupal\my_module;

            class Example {
              public function build(): void {
                $view = \Drupal::entityTypeManager()->getStorage('view')->lo<caret>ad('frontpage');
              }
            }
            """.trimIndent()
        )

        val types = reference.type.global(project).types
        assertContainsElements(types, "\\Drupal\\views\\Entity\\View")
    }

    fun testLoadMultipleOnConfigStorageResolvesToConfigEntityArray() {
        val reference = configureAndFindReference(
            $$"""
            <?php

            namespace Drupal\my_module;

            class Example {
              public function build(): void {
                $views = \Drupal::entityTypeManager()->getStorage('view')->loadMul<caret>tiple();
              }
            }
            """.trimIndent()
        )

        val types = reference.type.global(project).types
        assertContainsElements(types, "\\Drupal\\views\\Entity\\View[]")
    }

    private fun configureAndFindReference(text: String): MethodReference {
        myFixture.configureByText("Example.php", text)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        return generateSequence(element) { it.parent }
            .filterIsInstance<MethodReference>()
            .first()
    }

    private fun addConfigEntity() {
        myFixture.addFileToProject(
            "modules/views/src/Entity/View.php",
            $$"""
            <?php

            namespace Drupal\views\Entity;

            use Drupal\Core\Entity\Attribute\ConfigEntityType;

            #[ConfigEntityType(id: 'view')]
            class View {
            }
            """.trimIndent()
        )
    }

    private fun addDrupalStubs() {
        myFixture.addFileToProject(
            "stubs/drupal.php",
            $$"""
            <?php

            namespace Drupal\Core\Entity\Attribute {
              #[\Attribute]
              class ConfigEntityType {
                public function __construct(public string ${'$'}id = '') {}
              }
            }

            namespace Drupal\Core\Config\Entity {
              class ConfigEntityType {
                public function __construct(public string ${'$'}id = '') {}
              }

              interface ConfigEntityStorageInterface extends \Drupal\Core\Entity\EntityStorageInterface {
              }

              class ConfigEntityStorage implements ConfigEntityStorageInterface {
              }
            }

            namespace Drupal\Core\Entity {
              interface EntityInterface {
              }

              interface EntityStorageInterface {
                public function load(${'$'}id);
                public function loadMultiple(array ${'$'}ids = NULL);
              }

              interface EntityTypeManagerInterface {
                public function getStorage(${'$'}entity_type_id): EntityStorageInterface;
              }
            }

            namespace {
              class Drupal {
                public static function entityTypeManager(): \Drupal\Core\Entity\EntityTypeManagerInterface {}
              }
            }
            """.trimIndent()
        )
    }

    override fun getTestDataPath(): String = "src/test/testData"
}
