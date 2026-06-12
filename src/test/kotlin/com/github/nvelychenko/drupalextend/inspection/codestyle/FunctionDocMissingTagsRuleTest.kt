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

    // -------------------------------------------------------------------------
    // @throws: detection and generation
    // -------------------------------------------------------------------------

    fun testReportsMissingThrowsForDirectThrow() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\my_module\Exception\DocumentGenerationException;

            class Example {

              /**
               * Generates the document.
               */
              public function generate(): void {
                throw new DocumentGenerationException('nope');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Missing @throws for \\Drupal\\my_module\\Exception\\DocumentGenerationException"
        )
    }

    fun testDocumentedThrowsByShortNameIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\my_module\Exception\DocumentGenerationException;

            class Example {

              /**
               * Generates the document.
               *
               * @throws DocumentGenerationException
               *   Thrown when generation fails.
               */
              public function generate(): void {
                throw new DocumentGenerationException('nope');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "A @throws documented by short name must count as documented",
            descriptions.any { it.startsWith("Missing @throws") }
        )
    }

    fun testThrowInsideTryBlockIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(): void {
              try {
                throw new \RuntimeException('handled');
              }
              catch (\RuntimeException ${'$'}e) {
                // Swallowed.
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "A throw inside a try block is assumed handled and must not be reported",
            descriptions.any { it.startsWith("Missing @throws") }
        )
    }

    fun testRethrowInCatchBlockIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Entity\EntityStorageException;

            class Example {

              /**
               * Saves the thing.
               */
              public function save(): void {
                try {
                  ${'$'}this->doSave();
                }
                catch (EntityStorageException ${'$'}e) {
                  throw ${'$'}e;
                }
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Missing @throws for \\Drupal\\Core\\Entity\\EntityStorageException"
        )
    }

    fun testThrowInsideClosureIsNotReportedForOuterFunction() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Does something.
             */
            function example_do(): void {
              ${'$'}callback = function () {
                throw new \RuntimeException('belongs to the closure');
              };
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "A throw inside a nested closure must not be attributed to the outer function",
            descriptions.any { it.startsWith("Missing @throws") }
        )
    }

    fun testFixAddsThrowsAfterReturnGroup() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\my_module\Exception\DocumentGenerationException;

            class Example {

              /**
               * Generates the document.
               */
              public function generate(string ${'$'}id): string {
                if (${'$'}id === '') {
                  throw new DocumentGenerationException('empty id');
                }
                return ${'$'}id;
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\my_module\Exception\DocumentGenerationException;

            class Example {

              /**
               * Generates the document.
               *
               * @param string ${'$'}id
               *   The id.
               *
               * @return string
               *   The string.
               *
               * @throws \Drupal\my_module\Exception\DocumentGenerationException
               *   Thrown when the document generation exception occurs.
               */
              public function generate(string ${'$'}id): string {
                if (${'$'}id === '') {
                  throw new DocumentGenerationException('empty id');
                }
                return ${'$'}id;
              }

            }
            """.trimIndent()
        )
    }

    fun testFixAppendsThrowsToExistingThrowsGroup() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\my_module\Exception\FirstException;
            use Drupal\my_module\Exception\SecondException;

            class Example {

              /**
               * Does the thing.
               *
               * @throws \Drupal\my_module\Exception\FirstException
               *   Thrown when the first thing fails.
               */
              public function doThing(bool ${'$'}flag): void {
                if (${'$'}flag) {
                  throw new FirstException();
                }
                throw new SecondException();
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(
            "Second exception must be appended to the existing @throws group, got:\n$result",
            result.contains(
                " *   Thrown when the first thing fails.\n" +
                    "   * @throws \\Drupal\\my_module\\Exception\\SecondException"
            )
        )
        assertFalse(
            "Already documented exception must not be duplicated",
            Regex("""@throws \\Drupal\\my_module\\Exception\\FirstException""").findAll(result).count() > 1
        )
    }

    // -------------------------------------------------------------------------
    // @throws: propagation from called functions' docblocks
    // -------------------------------------------------------------------------

    fun testReportsThrowsPropagatedFromCalledFunction() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Saves the thing.
             *
             * @throws \Drupal\Core\Entity\EntityStorageException
             *   Thrown when the save fails.
             */
            function example_save(): void {}

            /**
             * Runs the import.
             */
            function example_import(): void {
              example_save();
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Missing @throws for \\Drupal\\Core\\Entity\\EntityStorageException"
        )
    }

    fun testReportsThrowsPropagatedThroughInheritdocToInterface() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            interface ExampleStorageInterface {

              /**
               * Saves the entity.
               *
               * @throws \Drupal\Core\Entity\EntityStorageException
               *   Thrown when the save fails.
               */
              public function save(): void;

            }

            class ExampleStorage implements ExampleStorageInterface {

              /**
               * {@inheritdoc}
               */
              public function save(): void {}

            }

            class ExampleConsumer {

              /**
               * Runs the import.
               */
              public function import(ExampleStorage ${'$'}storage): void {
                ${'$'}storage->save();
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Missing @throws for \\Drupal\\Core\\Entity\\EntityStorageException"
        )
    }

    fun testPropagatedThrowsInsideTryBlockIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Saves the thing.
             *
             * @throws \Drupal\Core\Entity\EntityStorageException
             *   Thrown when the save fails.
             */
            function example_save(): void {}

            /**
             * Runs the import.
             */
            function example_import(): void {
              try {
                example_save();
              }
              catch (\Exception ${'$'}e) {
                // Logged elsewhere.
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "A call inside a try block is assumed handled and must not propagate @throws",
            descriptions.any { it.startsWith("Missing @throws") }
        )
    }

    fun testPropagatedUncheckedExceptionIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Computes the thing.
             *
             * @throws \RuntimeException
             *   Thrown when the computation fails.
             */
            function example_compute(): void {}

            /**
             * Runs the import.
             */
            function example_import(): void {
              example_compute();
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Unchecked exception families must not be propagated to callers",
            descriptions.any { it.startsWith("Missing @throws") }
        )
    }

    fun testCallerDocumentingPropagatedThrowsIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Saves the thing.
             *
             * @throws \Drupal\Core\Entity\EntityStorageException
             *   Thrown when the save fails.
             */
            function example_save(): void {}

            /**
             * Runs the import.
             *
             * @throws \Drupal\Core\Entity\EntityStorageException
             *   Thrown when the save fails.
             */
            function example_import(): void {
              example_save();
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "A caller documenting the propagated exception must be silent",
            descriptions.any { it.startsWith("Missing @throws") }
        )
    }

    fun testFixAddsPropagatedThrows() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Saves the thing.
             *
             * @throws \Drupal\Core\Entity\EntityStorageException
             *   Thrown when the save fails.
             */
            function example_save(): void {}

            /**
             * Runs the import.
             */
            function example_import(): void {
              example_save();
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(
            "Propagated exception must be added to the caller's docblock, got:\n$result",
            result.contains(
                "/**\n" +
                    " * Runs the import.\n" +
                    " *\n" +
                    " * @throws \\Drupal\\Core\\Entity\\EntityStorageException\n" +
                    " *   Thrown when the entity storage exception occurs.\n" +
                    " */"
            )
        )
    }

    // -------------------------------------------------------------------------
    // Array shapes in generated @param / @return
    // -------------------------------------------------------------------------

    private fun applyAddMissingDocTags() {
        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)
    }

    fun testReturnArrayShapeFromLiteral() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\my_module\Value\Money;

            /**
             * Builds the summary.
             */
            function example_summary(): array {
              return [
                'id' => 1,
                'label' => 'Summary',
                'enabled' => TRUE,
                'amount' => new Money(),
              ];
            }
            """.trimIndent()
        )

        applyAddMissingDocTags()

        assertTrue(
            "Expected a literal-derived array shape, got:\n${myFixture.file.text}",
            myFixture.file.text.contains(
                "@return array{id: int, label: string, enabled: bool, amount: \\Drupal\\my_module\\Value\\Money}"
            )
        )
    }

    fun testReturnArrayShapeFromLocallyBuiltRenderArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Builds the render array.
             */
            function example_build(): array {
              ${'$'}build = [
                '#type' => 'markup',
              ];
              ${'$'}build['#markup'] = 'Hello';
              return ${'$'}build;
            }
            """.trimIndent()
        )

        applyAddMissingDocTags()

        assertTrue(
            "Expected a render-array shape with quoted keys, got:\n${myFixture.file.text}",
            myFixture.file.text.contains("@return array{'#type': string, '#markup': string}")
        )
    }

    fun testReturnArrayShapeMergesMultipleReturnsWithOptionalKeys() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Builds the result.
             */
            function example_result(bool ${'$'}detailed): array {
              if (${'$'}detailed) {
                return ['status' => 'ok', 'details' => 'all good'];
              }
              return ['status' => 'ok'];
            }
            """.trimIndent()
        )

        applyAddMissingDocTags()

        assertTrue(
            "Key present in only some returns must render optional, got:\n${myFixture.file.text}",
            myFixture.file.text.contains("@return array{status: string, details?: string}")
        )
    }

    fun testReturnUniformListRendersAsTypedArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Lists the names.
             */
            function example_names(): array {
              return ['one', 'two', 'three'];
            }
            """.trimIndent()
        )

        applyAddMissingDocTags()

        assertTrue(
            "Uniform literal list must render as string[], got:\n${myFixture.file.text}",
            myFixture.file.text.contains("@return string[]")
        )
    }

    fun testParameterArrayShapeFromReads() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Applies the options.
             */
            function example_apply(array ${'$'}options): void {
              ${'$'}timeout = ${'$'}options['timeout'];
              ${'$'}retries = ${'$'}options['retries'] ?? 3;
            }
            """.trimIndent()
        )

        applyAddMissingDocTags()

        assertTrue(
            "Guarded read must render optional, got:\n${myFixture.file.text}",
            myFixture.file.text.contains("@param array{timeout: mixed, retries?: mixed} \$options")
        )
    }

    fun testDynamicReturnArrayFallsBackToPlainArray() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Builds the map.
             */
            function example_map(string ${'$'}key): array {
              return [${'$'}key => 'value'];
            }
            """.trimIndent()
        )

        applyAddMissingDocTags()

        assertTrue(
            "Dynamic keys must fall back to plain array, got:\n${myFixture.file.text}",
            myFixture.file.text.contains("@return array\n")
        )
    }

    // -------------------------------------------------------------------------
    // Fix: class-typed parameters are fully qualified via the use table
    // -------------------------------------------------------------------------

    fun testFixQualifiesClassTypesViaUseStatementsAliasAndNamespace() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Entity\EntityTypeManagerInterface;
            use Drupal\other_module\VeryLongServiceName as ShortAlias;

            class Example {

              /**
               * Does something.
               */
              public function doSomething(EntityTypeManagerInterface ${'$'}entityTypeManager, ShortAlias ${'$'}service, LocalHelper ${'$'}helper): \Drupal\Core\Session\AccountInterface {}

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add missing doc tags" }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(
            "Imported class must render as its use-statement FQN, got:\n$result",
            result.contains("@param \\Drupal\\Core\\Entity\\EntityTypeManagerInterface \$entityTypeManager")
        )
        assertTrue(
            "Aliased class must render as the aliased FQN, got:\n$result",
            result.contains("@param \\Drupal\\other_module\\VeryLongServiceName \$service")
        )
        assertTrue(
            "Un-imported class must qualify against the current namespace, got:\n$result",
            result.contains("@param \\Drupal\\my_module\\LocalHelper \$helper")
        )
        assertTrue(
            "Already-qualified return type must stay as written, got:\n$result",
            result.contains("@return \\Drupal\\Core\\Session\\AccountInterface")
        )
    }
}
