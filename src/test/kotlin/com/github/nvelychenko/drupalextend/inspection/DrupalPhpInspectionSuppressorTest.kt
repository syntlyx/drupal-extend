package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.inspections.PhpUnusedParameterInspection
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Function as PhpFunction

class DrupalPhpInspectionSuppressorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    fun testSuppressesUnnecessaryQualifierForDrupalRootClass() {
        val file = myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            ${'$'}db = \Drupal::database();
            """.trimIndent()
        )
        val classReference = file.classReference("\\Drupal")
        val suppressor = DrupalPhpInspectionSuppressor()

        assertTrue(suppressor.isSuppressedFor(classReference, "PhpUnnecessaryFullyQualifiedNameInspection"))
        assertTrue(suppressor.isSuppressedFor(classReference, "PhpFullyQualifiedNameUsageInspection"))
        assertTrue(
            suppressor.isSuppressedFor(
                classReference.firstChild,
                "PhpUnnecessaryFullyQualifiedNameInspection"
            )
        )
    }

    fun testDoesNotSuppressOtherInspectionsOrOtherClassReferences() {
        val file = myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            ${'$'}url = \Drupal\Core\Url::fromRoute('<front>');
            """.trimIndent()
        )
        val classReference = file.classReference("\\Drupal\\Core\\Url")
        val suppressor = DrupalPhpInspectionSuppressor()

        assertFalse(suppressor.isSuppressedFor(classReference, "PhpUnnecessaryFullyQualifiedNameInspection"))
        assertFalse(suppressor.isSuppressedFor(classReference, "PhpUnused"))
    }

    fun testSuppressesUnusedInspectionForDrupalHookFunctionAndParameters() {
        val file = myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            function nll_core_theme(array ${'$'}existing, string ${'$'}type, string ${'$'}theme, string ${'$'}path): array {
              ${'$'}local = [];
              return ${'$'}local;
            }
            """.trimIndent()
        )
        val function = file.phpFunction("nll_core_theme")
        val suppressor = DrupalPhpInspectionSuppressor()

        assertTrue(suppressor.isSuppressedFor(function, "PhpUnused"))
        assertTrue(suppressor.isSuppressedFor(function.nameNode?.psi ?: error("Expected function name"), "PhpUnused"))
        function.parameters.forEach { parameter ->
            assertTrue(suppressor.isSuppressedFor(parameter, "PhpUnused"))
            assertTrue(suppressor.isSuppressedFor(parameter.nameNode?.psi ?: error("Expected parameter name"), "PhpUnused"))
            assertTrue(
                suppressor.isSuppressedFor(
                    parameter.nameIdentifier ?: error("Expected parameter identifier"),
                    "PhpUnusedParameterInspection"
                )
            )
        }
    }

    fun testSuppressesRealUnusedParameterInspectionForDrupalHookParameters() {
        myFixture.enableInspections(PhpUnusedParameterInspection())

        myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            function nll_core_theme(array ${'$'}existing, string ${'$'}type, string ${'$'}theme, string ${'$'}path): array {
              return [];
            }
            """.trimIndent()
        )

        myFixture.checkHighlighting()
    }

    fun testUnusedParameterInspectionStillReportsRegularPhpFunctionParameters() {
        myFixture.enableInspections(PhpUnusedParameterInspection())

        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function helper(string <weak_warning descr="Unused parameter 'value'. The parameter's value is not used anywhere.">${'$'}value</weak_warning>): int {
              return 1;
            }
            """.trimIndent()
        )

        myFixture.checkHighlighting()
    }

    fun testSuppressesPropertyHookValueParameterTypeWarning() {
        myFixture.enableInspections(PhpUnusedParameterInspection())

        myFixture.configureByText(
            "ValueTrait.php",
            """
            <?php

            trait ValueTrait {
              protected Value ${'$'}value {
                set(Value ${'$'}value) {
                  ${'$'}this->value = ${'$'}value;
                }
              }
            }
            """.trimIndent()
        )

        myFixture.checkHighlighting()
    }

    fun testDoesNotSuppressUnusedLocalVariablesInsideDrupalHooks() {
        val file = myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            function nll_core_theme(): array {
              ${'$'}local = [];
              return [];
            }
            """.trimIndent()
        )
        val localVariable = file.findElementAt(file.text.indexOf("\$local"))
            ?: error("Expected local variable")

        assertFalse(DrupalPhpInspectionSuppressor().isSuppressedFor(localVariable, "PhpUnused"))
    }

    fun testDoesNotSuppressWhenDrupalExtendIsDisabled() {
        val file = myFixture.configureByText(
            "nll_core.module",
            """
            <?php

            ${'$'}db = \Drupal::database();
            """.trimIndent()
        )
        project.drupalExtendSettings.isEnabled = false

        assertFalse(
            DrupalPhpInspectionSuppressor().isSuppressedFor(
                file.classReference("\\Drupal"),
                "PhpUnnecessaryFullyQualifiedNameInspection"
            )
        )
    }

    private fun PsiFile.classReference(text: String): ClassReference {
        return PsiTreeUtil.findChildrenOfType(this, ClassReference::class.java)
            .firstOrNull { it.text == text }
            ?: error("Expected class reference $text")
    }

    private fun PsiFile.phpFunction(name: String): PhpFunction {
        return PsiTreeUtil.findChildrenOfType(this, PhpFunction::class.java)
            .firstOrNull { it.name == name }
            ?: error("Expected PHP function $name")
    }
}
