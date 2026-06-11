package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalHookMethodVisibilityInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        myFixture.enableInspections(DrupalHookMethodVisibilityInspection())
        addHookAttribute()
    }

    fun testProtectedHookMethodIsReportedAndFixedToPublic() {
        myFixture.configureByText(
            "ProtectedHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class ProtectedHooks {
              #[Hook('user_login')]
              protected function onLogin(): void {}
            }
            """.trimIndent()
        )

        assertContainsElements(highlightDescriptions(), "Hook method must be public")

        val fix = myFixture.getAllQuickFixes().single { it.text == "Make hook method public" }
        myFixture.launchAction(fix)
        myFixture.checkResult(
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class ProtectedHooks {
              #[Hook('user_login')]
              public function onLogin(): void {}
            }
            """.trimIndent()
        )
    }

    fun testPrivateHookMethodIsReported() {
        myFixture.configureByText(
            "PrivateHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class PrivateHooks {
              #[Hook('user_login')]
              private function onLogin(): void {}
            }
            """.trimIndent()
        )

        assertContainsElements(highlightDescriptions(), "Hook method must be public")
    }

    fun testStaticPublicHookMethodIsReportedAndStaticRemoved() {
        myFixture.configureByText(
            "StaticHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class StaticHooks {
              #[Hook('user_login')]
              public static function onLogin(): void {}
            }
            """.trimIndent()
        )

        assertContainsElements(highlightDescriptions(), "Hook method must not be static")

        val fix = myFixture.getAllQuickFixes().single { it.text == "Remove static from hook method" }
        myFixture.launchAction(fix)
        myFixture.checkResult(
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class StaticHooks {
              #[Hook('user_login')]
              public function onLogin(): void {}
            }
            """.trimIndent()
        )
    }

    fun testPublicHookMethodIsSilent() {
        myFixture.configureByText(
            "PublicHooks.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class PublicHooks {
              #[Hook('user_login')]
              public function onLogin(): void {}
            }
            """.trimIndent()
        )

        val descriptions = highlightDescriptions()
        assertFalse(descriptions.contains("Hook method must be public"))
        assertFalse(descriptions.contains("Hook method must not be static"))
    }

    fun testNonHookProtectedMethodIsSilent() {
        myFixture.configureByText(
            "PlainClass.php",
            """
            <?php

            namespace Drupal\my_module;

            class PlainClass {
              protected function helper(): void {}
            }
            """.trimIndent()
        )

        val descriptions = highlightDescriptions()
        assertFalse(descriptions.contains("Hook method must be public"))
        assertFalse(descriptions.contains("Hook method must not be static"))
    }

    fun testBareHookAttributeDoesNotCrash() {
        myFixture.configureByText(
            "BareHook.php",
            $$"""
            <?php

            namespace Drupal\my_module\Hook;

            use Drupal\Core\Hook\Attribute\Hook;

            class BareHook {
              #[Hook]
              protected function onLogin(): void {}
            }
            """.trimIndent()
        )

        // The bare attribute is still the Hook attribute, so a visibility problem is fine; the
        // point is that highlighting completes without an exception.
        assertNotNull(myFixture.doHighlighting())
    }

    private fun highlightDescriptions(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }

    private fun addHookAttribute() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Hook/Attribute/Hook.php",
            $$"""
            <?php

            namespace Drupal\Core\Hook\Attribute;

            #[\Attribute(\Attribute::TARGET_METHOD | \Attribute::TARGET_CLASS | \Attribute::IS_REPEATABLE)]
            class Hook {
              public function __construct(
                public string $hook = '',
                public ?string $method = null,
              ) {}
            }
            """.trimIndent()
        )
    }
}
