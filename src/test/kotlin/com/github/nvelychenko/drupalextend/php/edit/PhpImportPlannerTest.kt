package com.github.nvelychenko.drupalextend.php.edit

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.PhpClass

class PhpImportPlannerTest : BasePlatformTestCase() {
    fun testImportsInsertionEditKeepsSingleBlankLinesAroundNewUseBlock() {
        val file = myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            /**
             * Example.
             */
            final class Example {}
            """.trimIndent()
        )
        val phpClass = PsiTreeUtil.findChildOfType(file, PhpClass::class.java)
            ?: error("Expected PHP class")
        val planner = PhpImportPlanner(file.text, phpClass)

        assertEquals("FieldWidget", planner.typeName("\\Drupal\\Core\\Field\\Attribute\\FieldWidget"))
        val edit = planner.importsInsertionEdit()
            ?: error("Expected imports insertion edit")
        val result = file.text.replaceRange(edit.startOffset, edit.endOffset, edit.text)

        assertEquals(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Field\Attribute\FieldWidget;

            /**
             * Example.
             */
            final class Example {}
            """.trimIndent(),
            result
        )
    }

    fun testSortedImportsEditSortsSimpleClassImports() {
        val file = myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\z_module\Zed;
            use Drupal\a_module\Alpha;

            final class Example {}
            """.trimIndent()
        )
        val phpClass = PsiTreeUtil.findChildOfType(file, PhpClass::class.java)
            ?: error("Expected PHP class")

        val edit = PhpImportPlanner(file.text, phpClass).sortedImportsEdit()
            ?: error("Expected sorted imports edit")
        val result = file.text.replaceRange(edit.startOffset, edit.endOffset, edit.text)

        assertTrue(
            result,
            result.contains(
                """
            use Drupal\a_module\Alpha;
            use Drupal\z_module\Zed;
            """.trimIndent()
            )
        )
    }

    fun testSortedImportsEditSkipsMixedUseStatements() {
        val file = myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\z_module\Zed;
            use function Drupal\a_module\helper;

            final class Example {}
            """.trimIndent()
        )
        val phpClass = PsiTreeUtil.findChildOfType(file, PhpClass::class.java)
            ?: error("Expected PHP class")

        assertNull(PhpImportPlanner(file.text, phpClass).sortedImportsEdit())
    }
}
