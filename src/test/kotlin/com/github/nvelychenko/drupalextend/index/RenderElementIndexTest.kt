package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl

class RenderElementIndexTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Render/Attribute/RenderElement.php",
            $$"""
            <?php

            namespace Drupal\Core\Render\Attribute;

            #[\Attribute(\Attribute::TARGET_CLASS)]
            class RenderElement {
              public function __construct(string $id) {}
            }
            """.trimIndent()
        )
    }

    fun testAttributeOnlyRenderElementWithoutDocblockIsIndexed() {
        val psiFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Element/MyElement.php",
            $$"""
            <?php

            namespace Drupal\my_module\Element;

            use Drupal\Core\Render\Attribute\RenderElement;

            #[RenderElement('my_element')]
            class MyElement {
              public function getInfo(): array {
                return [
                  '#items' => [],
                  '#title' => '',
                ];
              }
            }
            """.trimIndent()
        )

        val map = RenderElementIndex().indexer.map(FileContentImpl.createByFile(psiFile.virtualFile, project))

        val element = map["my_element"]
        assertNotNull("Attribute-only render element without a docblock must be indexed", element)
        assertEquals("\\Drupal\\my_module\\Element\\MyElement", element!!.typeClass)
        assertContainsElements(element.properties.map { it.id }, "#items", "#title")
    }

    fun testBareRenderElementAttributeWithoutArgumentsIsSkipped() {
        val psiFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Element/Incomplete.php",
            $$"""
            <?php

            namespace Drupal\my_module\Element;

            use Drupal\Core\Render\Attribute\RenderElement;

            #[RenderElement]
            class Incomplete {
              public function getInfo(): array {
                return [];
              }
            }
            """.trimIndent()
        )

        val map = RenderElementIndex().indexer.map(FileContentImpl.createByFile(psiFile.virtualFile, project))
        assertEmpty(map.keys)
    }
}
