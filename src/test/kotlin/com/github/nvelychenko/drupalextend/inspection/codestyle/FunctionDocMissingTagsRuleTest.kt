package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FunctionDocMissingTagsRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleFunctionDocMissingTagsEnabled = true

        // Disable every other rule to keep highlights unambiguous.
        project.drupalExtendSettings.isCodeStyleClassFileNameEnabled = false
        project.drupalExtendSettings.isCodeStyleClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionNameEnabled = false
        project.drupalExtendSettings.isCodeStyleMemberVisibilityEnabled = false
        project.drupalExtendSettings.isCodeStyleConstantCasingEnabled = false
        project.drupalExtendSettings.isCodeStyleClosingPhpTagEnabled = false
        project.drupalExtendSettings.isCodeStyleFileWhitespaceEnabled = false
        project.drupalExtendSettings.isCodeStyleShortArraySyntaxEnabled = false
        project.drupalExtendSettings.isCodeStyleMultilineArrayStyleEnabled = false
        project.drupalExtendSettings.isCodeStyleElseIfSyntaxEnabled = false
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleClassCloseBraceSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleBlockBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleCastSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleOperatorSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleCommaSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleControlStructureSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleUseStatementSortingEnabled = false
        project.drupalExtendSettings.isCodeStyleFullyQualifiedClassNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineBeforeTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagGroupSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockBlankLineNormalizationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockContentAfterOpenEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingSummaryEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockMissingParamNameEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionIndentationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionRequiredEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionLineEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockTagDescriptionPunctuationEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockApiTagsEnabled = false
        project.drupalExtendSettings.isCodeStylePropertyNameEnabled = false
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
        project.drupalExtendSettings.isCodeStyleElsePlacementEnabled = false
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = false
        project.drupalExtendSettings.isCodeStyleInlineCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = false
        project.drupalExtendSettings.isCodeStyleFileDocCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleScopeKeywordSpacingEnabled = false
    }

    // -------------------------------------------------------------------------
    // Detection: missing @param tags are reported
    // -------------------------------------------------------------------------

    fun testReportsMissingParamForUntypedParameter() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(${'$'}value) {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Missing @param for \$value")
    }

    fun testReportsMissingParamForTypedParameter() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(string ${'$'}value) {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Missing @param for \$value")
    }

    // -------------------------------------------------------------------------
    // Detection: missing @return tag is reported
    // -------------------------------------------------------------------------

    fun testReportsMissingReturnTagForTypedReturnType() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_get(): string {
              return 'hello';
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Missing @return tag")
    }

    // -------------------------------------------------------------------------
    // No false positive: void and never return types are silent
    // -------------------------------------------------------------------------

    fun testVoidReturnTypeIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(): void {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "void return type must not trigger @return warning",
            descriptions.contains("Missing @return tag")
        )
    }

    fun testNeverReturnTypeIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_exit(): never {
              exit(1);
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "never return type must not trigger @return warning",
            descriptions.contains("Missing @return tag")
        )
    }

    // -------------------------------------------------------------------------
    // No false positive: {@inheritdoc} skips entirely
    // -------------------------------------------------------------------------

    fun testInheritDocSkipsRule() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * {@inheritdoc}
             */
            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "{@inheritdoc} must suppress all missing-tag warnings",
            descriptions.any { it.startsWith("Missing @param") || it.startsWith("Missing @return") }
        )
    }

    fun testInheritDocUpperCaseSkipsRule() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * {@inheritDoc}
             */
            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "{@inheritDoc} must suppress all missing-tag warnings",
            descriptions.any { it.startsWith("Missing @param") || it.startsWith("Missing @return") }
        )
    }

    // -------------------------------------------------------------------------
    // No false positive: function with no docblock is silent (MissingDocCommentRule handles it)
    // -------------------------------------------------------------------------

    fun testFunctionWithNoDocBlockIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Function without docblock must not trigger missing-tag warnings",
            descriptions.any { it.startsWith("Missing @param") || it.startsWith("Missing @return") }
        )
    }

    // -------------------------------------------------------------------------
    // No false positive: closure is silent
    // -------------------------------------------------------------------------

    fun testClosureIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            ${'$'}fn = function(string ${'$'}value): string {
              return ${'$'}value;
            };
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Closures must not trigger missing-tag warnings",
            descriptions.any { it.startsWith("Missing @param") || it.startsWith("Missing @return") }
        )
    }

    // -------------------------------------------------------------------------
    // No false positive: function with correct @param tags is silent
    // -------------------------------------------------------------------------

    fun testFunctionWithAllParamTagsIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             *
             * @param string ${'$'}value
             *   The value.
             * @param int ${'$'}count
             *   The count.
             *
             * @return string
             *   The result.
             */
            function example_do(string ${'$'}value, int ${'$'}count): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Function with all @param and @return tags must not be reported",
            descriptions.any { it.startsWith("Missing @param") || it.startsWith("Missing @return") }
        )
    }

    // -------------------------------------------------------------------------
    // Fix: summary-only docblock gets @param tags inserted
    // -------------------------------------------------------------------------

    fun testFixInsertsParamTagsAfterSummary() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(string ${'$'}value, int ${'$'}count) {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Does something.
             *
             * @param string ${'$'}value
             *   The value.
             * @param int ${'$'}count
             *   The count.
             */
            function example_do(string ${'$'}value, int ${'$'}count) {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: method missing one @param among existing ones → only that one inserted
    // -------------------------------------------------------------------------

    fun testFixInsertsMissingParamAfterExistingParams() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class Example {

              /**
               * Does something.
               *
               * @param string ${'$'}first
               *   The first.
               */
              public function doSomething(string ${'$'}first, int ${'$'}second): void {}

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            class Example {

              /**
               * Does something.
               *
               * @param string ${'$'}first
               *   The first.
               * @param int ${'$'}second
               *   The second.
               */
              public function doSomething(string ${'$'}first, int ${'$'}second): void {}

            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: missing @return tag is added
    // -------------------------------------------------------------------------

    fun testFixAddsReturnTagAfterParamGroup() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             *
             * @param string ${'$'}value
             *   The value.
             */
            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Does something.
             *
             * @param string ${'$'}value
             *   The value.
             *
             * @return string
             *   The string.
             */
            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: summary-only docblock with return type gets both @param and @return
    // -------------------------------------------------------------------------

    fun testFixInsertsBothParamsAndReturn() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Does something.
             *
             * @param string ${'$'}value
             *   The value.
             *
             * @return string
             *   The string.
             */
            function example_do(string ${'$'}value): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: promoted constructor properties are treated as parameters
    // -------------------------------------------------------------------------

    fun testPromotedConstructorParamsAreReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class MyService {

              /**
               * Constructs a MyService object.
               */
              public function __construct(
                private string ${'$'}name,
                protected int ${'$'}count,
              ) {}

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Missing @param for \$name", "Missing @param for \$count")
    }

    fun testFixInsertsTagsForPromotedConstructorProperties() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            class MyService {

              /**
               * Constructs a MyService object.
               */
              public function __construct(
                private string ${'$'}name,
                protected int ${'$'}count,
              ) {}

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            class MyService {

              /**
               * Constructs a MyService object.
               *
               * @param string ${'$'}name
               *   The name.
               * @param int ${'$'}count
               *   The count.
               */
              public function __construct(
                private string ${'$'}name,
                protected int ${'$'}count,
              ) {}

            }
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: nullable type rendered as Type|null
    // -------------------------------------------------------------------------

    fun testNullableParamTypeRenderedCorrectly() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(?string ${'$'}value) {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Does something.
             *
             * @param string|null ${'$'}value
             *   The value.
             */
            function example_do(?string ${'$'}value) {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: untyped parameter defaults to mixed
    // -------------------------------------------------------------------------

    fun testUntypedParamDefaultsToMixed() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(${'$'}value) {}
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Does something.
             *
             * @param mixed ${'$'}value
             *   The value.
             */
            function example_do(${'$'}value) {}
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Fix: no @return report for no declared return type
    // -------------------------------------------------------------------------

    fun testNoReturnTypeDeclarationProducesNoReturnReport() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do() {}
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Function with no return type declaration must not trigger @return warning",
            descriptions.contains("Missing @return tag")
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency: applying the fix twice changes nothing
    // -------------------------------------------------------------------------

    fun testFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(string ${'$'}value, int ${'$'}count): string {
              return ${'$'}value;
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
            .let { myFixture.launchAction(it) }

        val afterFirstFix = myFixture.file.text

        // Second pass — no further "Add missing doc tags" fixes should be available.
        myFixture.doHighlighting()
        val secondPassFixes = myFixture.getAllQuickFixes().filter { it.text == "Add missing doc tags" }

        assertTrue(
            "Fix must be idempotent: no 'Add missing doc tags' actions expected after first application",
            secondPassFixes.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
