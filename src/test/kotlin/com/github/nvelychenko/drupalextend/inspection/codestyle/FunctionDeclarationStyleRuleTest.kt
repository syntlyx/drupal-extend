package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FunctionDeclarationStyleRuleTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        project.drupalExtendSettings.isCodeStyleFunctionDeclarationStyleEnabled = true

        // Disable unrelated rules to keep assertions focused.
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
    }

    /**
     * A normal malformed multiline declaration (no comments) must still be detected and fixed.
     * This is a baseline sanity check that the refactored scanner doesn't break the common case.
     */
    fun testNormalMultilineFunctionDeclarationIsDetectedAndFixed() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            final class Example {
              public function build(string ${'$'}foo,
             string ${'$'}bar): array {
                return [];
              }

            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "The first parameter of a multiline function declaration must be on the line after the opening parenthesis",
            "Multiline function declaration parameters must be indented by two spaces",
            "Multiline function declarations must have a trailing comma after the last parameter",
            "The closing parenthesis of a multiline function declaration must be on a new line"
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline function declaration"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            final class Example {
              public function build(
                string ${'$'}foo,
                string ${'$'}bar,
              ): array {
                return [];
              }

            }
            """.trimIndent()
        )
    }

    /**
     * When a block comment /* ) */ containing a closing parenthesis appears inside the parameter
     * list, the scanner must not treat that ) as the closing paren of the parameter list.
     * Before the fix this produced a wrong closeParenOffset, causing the quick-fix to corrupt
     * the declaration.
     */
    fun testBlockCommentWithClosingParenInParameterListIsFixedCorrectly() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            // Deliberately malformed (first param not on the next line, no trailing comma).
            // The block comment "/* ) */" inside the param list is the trap for the scanner.
            """
            <?php

            function example_build(/* ) */ ${'$'}foo,
             ${'$'}bar): array {
              return [];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "The first parameter of a multiline function declaration must be on the line after the opening parenthesis"
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline function declaration"
        }
        myFixture.launchAction(intention)

        // The fix replaces everything between ( and the first parameter's PSI start offset with
        // the newline + indent, so the block comment that preceded $foo is removed. The key
        // correctness property is that $bar ends up properly indented and ) is on its own line —
        // which is only possible because the scanner correctly located the real ) (not the one
        // inside the comment).
        myFixture.checkResult(
            """
            <?php

            function example_build(
              ${'$'}foo,
              ${'$'}bar,
            ): array {
              return [];
            }
            """.trimIndent()
        )
    }

    /**
     * When a line comment // containing ) appears inside the parameter list on the same line as
     * a parameter, the scanner must skip past the end of that line before continuing to count
     * parentheses.
     */
    fun testLineCommentWithClosingParenInParameterListIsFixedCorrectly() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(${'$'}foo, // The closing paren ) is inside this comment.
             ${'$'}bar): array {
              return [];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "The first parameter of a multiline function declaration must be on the line after the opening parenthesis"
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline function declaration"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(
              ${'$'}foo, // The closing paren ) is inside this comment.
              ${'$'}bar,
            ): array {
              return [];
            }
            """.trimIndent()
        )
    }

    /**
     * When a shell-style # comment containing ) appears inside the parameter list, the scanner
     * must skip past it as a line comment.
     */
    fun testHashCommentWithClosingParenInParameterListIsFixedCorrectly() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(${'$'}foo, # ) end of paren misleads scanner
             ${'$'}bar): array {
              return [];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "The first parameter of a multiline function declaration must be on the line after the opening parenthesis"
        )

        val intention = myFixture.getAllQuickFixes().first {
            it.text == "Normalize multiline function declaration"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            <?php

            function example_build(
              ${'$'}foo, # ) end of paren misleads scanner
              ${'$'}bar,
            ): array {
              return [];
            }
            """.trimIndent()
        )
    }

    /**
     * A valid multiline declaration with a block comment containing ) must not be reported.
     */
    fun testValidMultilineFunctionWithBlockCommentContainingParenDoesNotReport() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(
              /* ) */ ${'$'}foo,
              ${'$'}bar,
            ): array {
              return [];
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(
            descriptions.contains("The first parameter of a multiline function declaration must be on the line after the opening parenthesis")
        )
        assertFalse(
            descriptions.contains("Multiline function declaration parameters must be indented by two spaces")
        )
        assertFalse(
            descriptions.contains("Multiline function declarations must have a trailing comma after the last parameter")
        )
        assertFalse(
            descriptions.contains("The closing parenthesis of a multiline function declaration must be on a new line")
        )
    }

    /**
     * Applying the fix twice must produce the same result (idempotency).
     */
    fun testFixIsIdempotent() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText(
            "Example.php",
            """
            <?php

            function example_build(${'$'}foo,
             ${'$'}bar): array {
              return [];
            }
            """.trimIndent()
        )

        // First application.
        myFixture.doHighlighting()
        myFixture.getAllQuickFixes().first { it.text == "Normalize multiline function declaration" }
            .let { myFixture.launchAction(it) }

        val afterFirstFix = myFixture.file.text

        // Second application: re-enable the inspection and check for further issues.
        myFixture.doHighlighting()
        val secondPassFixes = myFixture.getAllQuickFixes().filter {
            it.text == "Normalize multiline function declaration"
        }

        // No further normalize fixes should be available — the result is already correct.
        assertTrue(
            "Fix must be idempotent: no 'Normalize multiline function declaration' fixes expected after first application",
            secondPassFixes.isEmpty()
        )

        // The document must not have changed.
        assertEquals(afterFirstFix, myFixture.file.text)
    }
}
