package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [FullyQualifiedClassNameRule] covering the two bug fixes:
 *
 *  1. Case-insensitive namespace comparison — a FQN whose namespace segment
 *     differs from the file namespace only in letter case must not generate a
 *     spurious "import" suggestion.
 *
 *  2. Heredoc-blind regex replaced by PSI — a heredoc (or nowdoc) that
 *     contains a line textually resembling `use Foo\Bar;` or `namespace X;`
 *     must not corrupt the rule's view of real imports.
 */
class FullyQualifiedClassNameRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleFullyQualifiedClassNameEnabled = true
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
    }

    // -------------------------------------------------------------------------
    // Baseline: normal FQCN detection still works
    // -------------------------------------------------------------------------

    fun testReportsFullyQualifiedClassName() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function build(): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Fully qualified class names must be imported with use statements")
    }

    fun testQuickFixImportsAndShortensFqcn() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function build(): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Import class and shorten reference"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Url;

            final class Example {
              public function build(): Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )
    }

    fun testClassInSameNamespaceIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function build(): \Drupal\my_module\OtherClass {
                return new \Drupal\my_module\OtherClass();
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Class in the same namespace must not be reported as needing an import",
            descriptions.contains("Fully qualified class names must be imported with use statements")
        )
    }

    // -------------------------------------------------------------------------
    // Bug 1: case-insensitive namespace comparison
    // -------------------------------------------------------------------------

    /**
     * When the FQN's namespace segment matches the file namespace only in
     * letter case, the rule must NOT generate a suggestion (PHP namespaces are
     * case-insensitive).
     */
    fun testCaseVariantNamespaceSameAsFileNamespaceIsNotReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        // File declares `Drupal\My_Module` but the reference uses lower-case spelling.
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\My_Module;

            final class Example {
              public function build(): \drupal\my_module\OtherClass {
                return new \drupal\my_module\OtherClass();
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "FQN whose namespace matches the file namespace case-insensitively must not be reported",
            descriptions.contains("Fully qualified class names must be imported with use statements")
        )
    }

    /**
     * A class from a genuinely different namespace (different letters, not just
     * different case) must still be reported.
     */
    fun testClassFromDifferentNamespaceIsReported() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function build(): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Fully qualified class names must be imported with use statements"
        )
    }

    /**
     * If the file already has `use Drupal\Core\Foo;` and there is a reference
     * to `\drupal\core\Foo` (same FQN, different case), the rule should still
     * report that the FQCN should be shortened (the fix shortens to `Foo` using
     * the existing import, without adding a duplicate).
     *
     * The important case-insensitive fix here: the alias collision check
     * (`alias == shortName && fqn != normalizedFqn`) must NOT falsely treat
     * `Drupal\Core\Foo` and `drupal\core\Foo` as DIFFERENT FQNs and block
     * the candidate from being reported.  In the old case-sensitive code,
     * the collision check would fire and silently suppress the candidate, so the
     * reference was never shortened.  After the fix the candidate is reported and
     * the fix shortens the reference without adding a duplicate import.
     */
    fun testExistingImportWithDifferentCaseShortensFqcnWithoutDuplicatingImport() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Foo;

            final class Example {
              public function build(): \drupal\core\Foo {
                return NULL;
              }

            }
            """.trimIndent()
        )

        // The rule must report the FQCN — the reference should be shortened to `Foo`.
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Fully qualified class names must be imported with use statements"
        )

        // Applying the fix shortens the reference without adding a duplicate use import.
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Import class and shorten reference"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        // The reference must be shortened.
        assertTrue("Reference must be shortened to Foo", result.contains(": Foo {"))
        // Only one use statement for Foo must exist (no duplicate import).
        val useCount = result.lines().count { it.trim() == "use Drupal\\Core\\Foo;" }
        assertEquals("Must have exactly one use import for Drupal\\Core\\Foo", 1, useCount)
    }

    // -------------------------------------------------------------------------
    // Bug 2: heredoc-blind regex replaced by PSI
    // -------------------------------------------------------------------------

    /**
     * A heredoc whose body contains a line that looks like `use Foo\Bar;` must
     * not be treated as a real import by the rule.
     *
     * Before the fix the regex `(?m)^use\s+...;` would match inside the
     * heredoc body, making the rule believe `Foo\Bar` was already imported.
     */
    fun testHeredocContainingFakeUseLineDoesNotAffectImportDetection() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            // The heredoc body has a line that looks like a use statement.
            // It must NOT suppress the real FQCN warning on \Drupal\Core\Url.
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function getHelp(): string {
                return <<<EOT
            use Drupal\Core\Url;
            EOT;
              }

              public function build(): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        // The real use of \Drupal\Core\Url must still be flagged — the fake import
        // inside the heredoc must not be mistaken for a real one.
        assertContainsElements(
            descriptions,
            "Fully qualified class names must be imported with use statements"
        )
    }

    /**
     * Applying the quick-fix when a heredoc contains a fake `use` line must
     * insert the real import correctly and not be confused by the heredoc content.
     */
    fun testQuickFixInsertsRealImportIgnoringHeredocFakeUse() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function getHelp(): string {
                return <<<EOT
            use Drupal\Core\Url;
            EOT;
              }

              public function build(): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Import class and shorten reference"
        }
        myFixture.launchAction(intention)

        // A real `use Drupal\Core\Url;` must be inserted at the top of the file,
        // not blocked by the fake one inside the heredoc.
        val resultText = myFixture.file.text
        assertTrue(
            "A real use import must be inserted after the namespace declaration",
            resultText.contains(Regex("""(?m)^use Drupal\\Core\\Url;"""))
        )
    }

    /**
     * A nowdoc (single-quoted heredoc) whose body resembles `use Foo\Bar;` must
     * similarly not affect the rule's import detection.
     */
    fun testNowdocContainingFakeUseLineDoesNotAffectImportDetection() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public const HELP = <<<'EOT'
            use Drupal\Core\Url;
            EOT;

              public function build(): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(
            descriptions,
            "Fully qualified class names must be imported with use statements"
        )
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    /**
     * Applying the "Import class and shorten reference" fix twice must leave
     * the file unchanged after the first application.
     *
     * The file uses the FQCN only as a return type (single occurrence) so that
     * the per-element fix resolves all warnings in one shot.
     */
    fun testFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            final class Example {
              public function build(): \Drupal\Core\Url {
                return NULL;
              }

            }
            """.trimIndent()
        )

        // First application — shortens the single FQCN and adds the import.
        myFixture.doHighlighting()
        val firstIntention = myFixture.getAllQuickFixes().first {
            it.text == "Import class and shorten reference"
        }
        myFixture.launchAction(firstIntention)

        val afterFirstFix = myFixture.file.text

        // After the fix there should be no more FQCN warnings for this class.
        myFixture.doHighlighting()
        val secondIntentions = myFixture.getAllQuickFixes().filter {
            it.text == "Import class and shorten reference"
        }
        assertTrue(
            "After fixing once, no further 'Import class and shorten reference' actions should be available",
            secondIntentions.isEmpty()
        )

        // The text must be stable.
        assertEquals(afterFirstFix, myFixture.file.text)
    }

    /**
     * Applying the "Fix Drupal code style in file" fix twice must also be
     * idempotent.
     */
    fun testFileLevelFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Cache\CacheBackendInterface;

            final class Example {
              public function build(\Drupal\Core\StringTranslation\TranslatableMarkup ${'$'}label): \Drupal\Core\Url {
                return \Drupal\Core\Url::fromRoute('example.route');
              }

            }
            """.trimIndent()
        )

        // First application
        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        val afterFirstFix = myFixture.file.text

        // No further FQCN warnings.
        myFixture.doHighlighting()
        val remainingFqcn = myFixture.doHighlighting().filter {
            it.description == "Fully qualified class names must be imported with use statements"
        }
        assertTrue(
            "No FQCN warnings must remain after the file-level fix",
            remainingFqcn.isEmpty()
        )

        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
