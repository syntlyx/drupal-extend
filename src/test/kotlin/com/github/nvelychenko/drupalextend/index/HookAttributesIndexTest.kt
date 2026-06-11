package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl

class HookAttributesIndexTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        addHookAttributeClass()
    }

    fun testBareHookAttributeIsSkippedAndValidHookIsIndexed() {
        // A bare `#[Hook]` (no arguments yet) is normal mid-typing PHP and must not break
        // indexing of the rest of the class. The index extension is registered in the optional
        // `drupal.xml` (absent in tests), so the DataIndexer is exercised directly.
        val psiFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Hook/MyModuleHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyModuleHooks {

              #[Hook]
              public function incomplete(): void {}

              #[Hook('node_view')]
              public function nodeView(): void {}

            }
            """.trimIndent()
        )

        val fileContent = FileContentImpl.createByFile(psiFile.virtualFile, project)
        val map = HookAttributesIndex().indexer.map(fileContent)
        assertEquals(mapOf("node_view" to "\\Drupal\\my_module\\Hook\\MyModuleHooks"), map)
    }

    fun testAllClassesInOneFileAreIndexed() {
        val psiFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Hook/MyModuleAllHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyModuleHooks {

              #[Hook('node_view')]
              public function nodeView(): void {}

            }

            class MyModuleFormHooks {

              #[Hook('form_alter')]
              public function formAlter(): void {}

            }
            """.trimIndent()
        )

        val fileContent = FileContentImpl.createByFile(psiFile.virtualFile, project)
        val map = HookAttributesIndex().indexer.map(fileContent)
        assertEquals(
            mapOf(
                "node_view" to "\\Drupal\\my_module\\Hook\\MyModuleHooks",
                "form_alter" to "\\Drupal\\my_module\\Hook\\MyModuleFormHooks",
            ),
            map
        )
    }

    fun testGetHookAttributesByNameSkipsBareHookAttribute() {
        val file = myFixture.configureByText(
            "MyModuleHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyModuleHooks {

              #[Hook]
              public function incomplete(): void {}

              #[Hook('node_view')]
              public function nodeView(): void {}

            }
            """.trimIndent()
        )

        val attributes = getHookAttributesByName(file, "node_view")
        assertSize(1, attributes)
        assertEmpty(getHookAttributesByName(file, "node_presave"))
    }

    private fun addHookAttributeClass() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Hook/Attribute/Hook.php",
            $$"""
            <?php

            namespace Drupal\Core\Hook\Attribute;

            #[\Attribute(\Attribute::TARGET_METHOD | \Attribute::IS_REPEATABLE)]
            class Hook {
              public function __construct(string $hook, string $method = '', ?int $priority = null, ?string $module = null) {}
            }
            """.trimIndent()
        )
    }
}
