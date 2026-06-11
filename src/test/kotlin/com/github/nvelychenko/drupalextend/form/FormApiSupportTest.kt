package com.github.nvelychenko.drupalextend.form

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.usage.DrupalImplicitUsageProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class FormApiSupportTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    fun testSubmitHandlerStringResolvesToOwnMethod() {
        val file = configureForm(
            $$"""
            public function buildForm(array $form, $form_state): array {
              $form['#submit'] = ['::submitHandler'];
              return $form;
            }

            public function submitHandler(array &$form, $form_state): void {}
            """
        )
        val phpClass = file.phpClass("MyForm")

        assertEquals(phpClass.method("submitHandler"), resolve(file, "::submitHandler"))
    }

    fun testAjaxCallbackResolvesToOwnMethod() {
        val file = configureForm(
            $$"""
            public function buildForm(array $form, $form_state): array {
              $form['field'] = [
                '#ajax' => [
                  'callback' => '::ajaxCallback',
                ],
              ];
              return $form;
            }

            public function ajaxCallback(array &$form, $form_state): array {
              return $form;
            }
            """
        )
        val phpClass = file.phpClass("MyForm")

        assertEquals(phpClass.method("ajaxCallback"), resolve(file, "::ajaxCallback"))
    }

    fun testAppendingSubmitAssignmentResolves() {
        val file = configureForm(
            $$"""
            public function buildForm(array $form, $form_state): array {
              $form['#submit'][] = '::submitHandler';
              return $form;
            }

            public function submitHandler(array &$form, $form_state): void {}
            """
        )
        val phpClass = file.phpClass("MyForm")

        assertEquals(phpClass.method("submitHandler"), resolve(file, "::submitHandler"))
    }

    fun testArbitraryStringInUnrelatedArrayHasNoCallbackReference() {
        val file = configureForm(
            $$"""
            public function buildForm(array $form, $form_state): array {
              $form['#title'] = ['::notACallback'];
              return $form;
            }

            public function notACallback(): void {}
            """
        )

        val literal = literal(file, "::notACallback")
        assertNull(literal.references.firstOrNull { it.resolve() != null })
    }

    fun testCallbackMethodIsImplicitlyUsed() {
        val file = configureForm(
            $$"""
            public function buildForm(array $form, $form_state): array {
              $form['#submit'] = ['::submitHandler'];
              return $form;
            }

            public function submitHandler(array &$form, $form_state): void {}

            public function unrelated(): void {}
            """
        )
        val phpClass = file.phpClass("MyForm")
        val provider = DrupalImplicitUsageProvider()

        assertTrue(provider.isImplicitUsage(phpClass.method("submitHandler")))
        assertFalse(provider.isImplicitUsage(phpClass.method("unrelated")))
    }

    fun testGetValueCompletionSuggestsFormKeys() {
        myFixture.configureByText(
            "MyForm.php",
            $$"""
            <?php

            namespace Drupal\my_module\Form;

            class MyForm {
              public function buildForm(array $form, $form_state): array {
                $form['applicant_name'] = ['#type' => 'textfield'];
                $form['loan_amount']['inner'] = ['#type' => 'number'];
                return $form;
              }

              public function submitForm(array &$form, $form_state): void {
                $value = $form_state->getValue('<caret>');
              }
            }
            """.trimIndent()
        )

        val suggestions = myFixture.completeBasic()
            ?.map { it.lookupString }
            ?: emptyList()

        assertTrue("Expected applicant_name", suggestions.contains("applicant_name"))
        assertTrue("Expected loan_amount", suggestions.contains("loan_amount"))
    }

    fun testGetValueOnNonFormStateVariableHasNoSuggestions() {
        myFixture.configureByText(
            "MyForm.php",
            $$"""
            <?php

            namespace Drupal\my_module\Form;

            class MyForm {
              public function buildForm(array $form, $form_state): array {
                $form['applicant_name'] = ['#type' => 'textfield'];
                return $form;
              }

              public function submitForm(array &$form, $form_state): void {
                $other = $unrelated->getValue('<caret>');
              }
            }
            """.trimIndent()
        )

        val suggestions = myFixture.completeBasic()
            ?.map { it.lookupString }
            ?: emptyList()

        assertFalse("Form keys must not leak through a non form-state receiver", suggestions.contains("applicant_name"))
    }

    private fun resolve(file: PsiFile, contents: String) =
        literal(file, contents).references.firstNotNullOfOrNull { it.resolve() }

    private fun literal(file: PsiFile, contents: String): StringLiteralExpression =
        PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)
            .firstOrNull { it.contents == contents }
            ?: error("Expected string literal $contents")

    private fun configureForm(body: String): PsiFile = myFixture.configureByText(
        "MyForm.php",
        $$"""
        <?php

        namespace Drupal\my_module\Form;

        class MyForm {
        $$body
        }
        """.trimIndent()
    )

    private fun PsiFile.phpClass(name: String): PhpClass =
        PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .firstOrNull { it.name == name }
            ?: error("Expected PHP class $name")

    private fun PhpClass.method(name: String): Method =
        findMethodByName(name) ?: error("Expected method $name")
}
