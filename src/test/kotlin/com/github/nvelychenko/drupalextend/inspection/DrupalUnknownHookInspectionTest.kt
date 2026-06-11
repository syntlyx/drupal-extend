package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalUnknownHookInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        // drupal.xml is not loaded in tests (the optional php.drupal plugin is absent), so the
        // inspection isn't registered as an EP — enable it via an instance instead of its class.
        myFixture.enableInspections(DrupalUnknownHookInspection())

        // The `#[Hook]` attribute class the inspection keys on.
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Hook/Attribute/Hook.php",
            """
            <?php

            namespace Drupal\Core\Hook\Attribute;

            #[\Attribute(\Attribute::TARGET_METHOD | \Attribute::TARGET_CLASS)]
            class Hook {
                public function __construct(public string ${'$'}hook) {}
            }
            """.trimIndent()
        )

        // Known-hook source via PhpFunctionNameIndex: api.php doc functions. One plain hook and
        // one with an UPPERCASE placeholder segment for the dynamic-pattern case.
        myFixture.addFileToProject(
            "core/modules/node/node.api.php",
            """
            <?php

            function hook_node_view() {}

            function hook_form_FORM_ID_alter() {}
            """.trimIndent()
        )
    }

    fun testKnownExactHookHasNoWarning() {
        val warnings = warningsFor(
            """
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyHooks {
                #[Hook('node_view')]
                public function nodeView() {}
            }
            """.trimIndent()
        )

        assertEmpty(warnings)
    }

    fun testDynamicPatternHookHasNoWarning() {
        val warnings = warningsFor(
            """
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyHooks {
                #[Hook('form_my_form_alter')]
                public function formAlter() {}
            }
            """.trimIndent()
        )

        assertEmpty(warnings)
    }

    fun testUnknownHookIsFlagged() {
        val warnings = warningsFor(
            """
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyHooks {
                #[Hook('totally_made_up_hook')]
                public function nope() {}
            }
            """.trimIndent()
        )

        assertTrue(
            "Expected a warning for the unknown hook, got: $warnings",
            warnings.any { it.contains("Unknown hook 'totally_made_up_hook'") }
        )
    }

    fun testBareHookAttributeDoesNotCrashOrWarn() {
        val warnings = warningsFor(
            """
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class MyHooks {
                #[Hook]
                public function nope() {}
            }
            """.trimIndent()
        )

        assertFalse(warnings.any { it.contains("Unknown hook") })
    }

    private fun warningsFor(content: String): List<String> {
        myFixture.configureByText("MyHooks.php", content)
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("Unknown hook") }
    }
}
