package com.github.nvelychenko.drupalextend.config

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.ConfigObjectReference
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class BundleValueSupportTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        addNodeEntity()
        addBundleConfigs()
    }

    fun testEntityQueryConditionValueCompletesBundles() {
        addDrupalClassWithEntityQuery()
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            \Drupal::entityQuery('node')->condition('type', 'art<caret>');
            """.trimIndent()
        )

        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups == null) {
            assertTrue(myFixture.file.text.contains("condition('type', 'article')"))
            return
        }

        assertContainsElements(lookups.map { it.lookupString }, "article")
    }

    fun testConditionOnNonBundleFieldGetsNoBundles() {
        addDrupalClassWithEntityQuery()
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            \Drupal::entityQuery('node')->condition('status', '<caret>');
            """.trimIndent()
        )

        val lookups = myFixture.complete(CompletionType.BASIC) ?: emptyArray()

        assertDoesntContain(lookups.map { it.lookupString }, "article", "page")
    }

    fun testStaticCreateArrayValueCompletesBundles() {
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            \Drupal\node\Entity\Node::create(['type' => 'pa<caret>']);
            """.trimIndent()
        )

        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups == null) {
            assertTrue(myFixture.file.text.contains("['type' => 'page']"))
            return
        }

        assertContainsElements(lookups.map { it.lookupString }, "page")
    }

    fun testDrupalCreateNodeCompletesBundles() {
        myFixture.configureByText(
            "SomeTest.php",
            $$"""
            <?php

            class SomeTestClass {
              public function helper(): void {
                $this->drupalCreateNode(['type' => 'art<caret>']);
              }
            }
            """.trimIndent()
        )

        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups == null) {
            assertTrue(myFixture.file.text.contains("['type' => 'article']"))
            return
        }

        assertContainsElements(lookups.map { it.lookupString }, "article")
    }

    fun testBundleValueGetsConfigReference() {
        val file = myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            \Drupal\node\Entity\Node::create(['type' => 'article']);
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)
            .single { it.contents == "article" }
        val reference = literal.references.filterIsInstance<ConfigObjectReference>().singleOrNull()

        assertNotNull("Bundle value must reference its config object", reference)
        assertEquals("node.type.article", reference!!.canonicalText)
        assertTrue("Reference must resolve to the bundle config file", reference.multiResolve(false).isNotEmpty())
    }

    fun testUnrelatedArrayValueGetsNoBundleReference() {
        val file = myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            some_function(['type' => 'article']);
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)
            .single { it.contents == "article" }

        assertEmpty(literal.references.filterIsInstance<ConfigObjectReference>())
    }

    private fun addNodeEntity() {
        myFixture.addFileToProject(
            "core/modules/node/src/Entity/Node.php",
            """
            <?php
            namespace Drupal\node\Entity;

            /**
             * @ContentEntityType(
             *   id = "node",
             *   entity_keys = {
             *     "id" = "nid",
             *     "bundle" = "type",
             *   },
             * )
             */
            class Node {
              public static function create(array ${'$'}values = []) {}
            }
            """.trimIndent()
        )
    }

    private fun addBundleConfigs() {
        myFixture.addFileToProject(
            "modules/custom/my_module/config/install/node.type.article.yml",
            """
            name: Article
            type: article
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/config/install/node.type.page.yml",
            """
            name: Page
            type: page
            """.trimIndent()
        )
    }

    private fun addDrupalClassWithEntityQuery() {
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function entityQuery(string $entity_type_id): object {}
            }
            """.trimIndent()
        )
    }
}
