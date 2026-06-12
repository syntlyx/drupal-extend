package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MissingDocCommentRuleTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleMissingDocCommentsEnabled = true
        // Disable every other rule so highlights are unambiguous.
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
        project.drupalExtendSettings.isCodeStyleDocBlockShortTypesEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockSummaryLengthEnabled = false
        project.drupalExtendSettings.isCodeStyleElsePlacementEnabled = false
        project.drupalExtendSettings.isCodeStyleStringQuotesEnabled = false
        project.drupalExtendSettings.isCodeStyleInlineCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleDiscouragedFunctionsEnabled = false
        project.drupalExtendSettings.isCodeStylePropertyNameEnabled = false
        project.drupalExtendSettings.isCodeStyleScopeKeywordSpacingEnabled = false
        project.drupalExtendSettings.isCodeStyleConsecutiveBlankLinesEnabled = false
        project.drupalExtendSettings.isCodeStyleFileDocCommentEnabled = false
        project.drupalExtendSettings.isCodeStyleFunctionDocMissingTagsEnabled = false
        project.drupalExtendSettings.isCodeStyleDocBlockDeclarationSpacingEnabled = false
    }

    fun testReportsUndocumentedClassConstant() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Package types.
             */
            final class Example {

              public const string LOAN_PACKAGE = 'loan_package';

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Missing constant doc comment")
    }

    fun testReportsUndocumentedProperty() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Example.
             */
            class Example {

              protected \Drupal\Core\Entity\EntityTypeManagerInterface ${'$'}entityTypeManager;

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertContainsElements(descriptions, "Missing member variable doc comment")
    }

    fun testDocumentedConstantIsSilent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Package types.
             */
            final class Example {

              /**
               * The loan package.
               */
              public const string LOAN_PACKAGE = 'loan_package';

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(
            "Documented constant must not be reported",
            descriptions.any { it.startsWith("Missing constant doc comment") }
        )
    }

    fun testFixGeneratesConstantDocBlock() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Package types.
             */
            final class Example {

              public const string LOAN_PACKAGE = 'loan_package';

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add member doc comment" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            /**
             * Package types.
             */
            final class Example {

              /**
               * The loan package.
               */
              public const string LOAN_PACKAGE = 'loan_package';

            }
            """.trimIndent()
        )
    }

    fun testFixGeneratesPropertyDocBlockWithVarType() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Entity\EntityTypeManagerInterface;

            /**
             * Example.
             */
            class Example {

              protected EntityTypeManagerInterface ${'$'}entityTypeManager;

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first { it.text == "Add member doc comment" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\Entity\EntityTypeManagerInterface;

            /**
             * Example.
             */
            class Example {

              /**
               * The entity type manager.
               *
               * @var \Drupal\Core\Entity\EntityTypeManagerInterface
               */
              protected EntityTypeManagerInterface ${'$'}entityTypeManager;

            }
            """.trimIndent()
        )
    }

    fun testFileWideFixDocumentsAllConstantsInOneRun() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            /**
             * Package types.
             */
            final class GeneratedDocumentPackageType {

              public const string LOAN_PACKAGE = 'loan_package';

              public const string STATE_NOTICE = 'state_notice';

              public const string DOCUMENT_ARCHIVE = 'document_archive';

            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Fix Drupal code style in file"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue(result.contains(" * The loan package.\n   */\n  public const string LOAN_PACKAGE"))
        assertTrue(result.contains(" * The state notice.\n   */\n  public const string STATE_NOTICE"))
        assertTrue(result.contains(" * The document archive.\n   */\n  public const string DOCUMENT_ARCHIVE"))
    }
}
