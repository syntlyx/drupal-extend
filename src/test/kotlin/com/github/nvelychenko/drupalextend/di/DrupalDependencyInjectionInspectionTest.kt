package com.github.nvelychenko.drupalextend.di

import com.github.nvelychenko.drupalextend.inspection.DrupalDependencyInjectionInspection
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalDependencyInjectionInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
        project.drupalExtendSettings.isDependencyInjectionHelperEnabled = true
    }

    fun testInspectionReportsStaticDrupalServiceAccessInInstanceMethod() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              public function build(): array {
                $label = \Drupal::service('my_module.sample_manager')->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Use constructor injection for service 'my_module.sample_manager'"
        )
    }

    fun testInspectionCanBeDisabledFromSettings() {
        project.drupalExtendSettings.isDependencyInjectionHelperEnabled = false
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              public function build(): array {
                $label = \Drupal::service('my_module.sample_manager')->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Use constructor injection for service 'my_module.sample_manager'"))
    }

    fun testInspectionUsesKnownFallbackForCurrentUser() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addDrupalCoreInterfaces()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              public function build(): array {
                $account_id = \Drupal::currentUser()->id();
                return ['#markup' => (string) $account_id];
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Use constructor injection for service 'current_user'"
        )
    }

    fun testInspectionSkipsStaticMethodsAndExistingInjectedField() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              private \Drupal\my_module\SampleManager $sampleManager;

              public static function staticBuild(): void {
                \Drupal::service('my_module.sample_manager')->label();
              }

              public function build(): array {
                $label = \Drupal::service('my_module.sample_manager')->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Use constructor injection for service 'my_module.sample_manager'"))
    }

    fun testQuickFixInjectsServiceIntoNewConstructor() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              public function build(): array {
                $label = \Drupal::service('my_module.sample_<caret>manager')->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Inject service 'my_module.sample_manager' into constructor"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertContainsText(result, "use Drupal\\my_module\\SampleManager;")
        assertContainsText(result, $$"protected SampleManager $sampleManager;")
        assertContainsText(result, $$"public function __construct(SampleManager $sample_manager) {")
        assertContainsText(result, $$"$this->sampleManager = $sample_manager;")
        assertContainsText(result, $$"$label = $this->sampleManager->label();")
        assertFalse(result.contains("public static function create("))
    }

    fun testQuickFixInjectsControllerServiceWithCreateMethodAndImports() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addControllerBaseClass()
        addContainerInterface()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            use Drupal\Core\Controller\ControllerBase;

            class ExampleController extends ControllerBase {
              public function build(): array {
                $label = \Drupal::service('my_module.sample_<caret>manager')->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Inject service 'my_module.sample_manager' into constructor"
        }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            use Drupal\Core\Controller\ControllerBase;
            use Drupal\my_module\SampleManager;
            use Symfony\Component\DependencyInjection\ContainerInterface;

            class ExampleController extends ControllerBase {
              protected SampleManager $sampleManager;

              public function __construct(SampleManager $sample_manager) {
                $this->sampleManager = $sample_manager;
              }

              public static function create(ContainerInterface $container): static {
                return new static(
                  $container->get('my_module.sample_manager'),
                );
              }

              public function build(): array {
                $label = $this->sampleManager->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )
    }

    fun testQuickFixUpdatesExistingConstructorAndCreateMethod() {
        assertCreateMethodQuickFix(
            "new static('Sample')",
            $$"return new static('Sample', $container->get('my_module.sample_manager'));"
        )
    }

    fun testQuickFixUpdatesCreateMethodWithSelfConstructor() {
        assertCreateMethodQuickFix(
            "new self('Sample')",
            $$"return new self('Sample', $container->get('my_module.sample_manager'));"
        )
    }

    fun testQuickFixUpdatesCreateMethodWithClassNameConstructor() {
        assertCreateMethodQuickFix(
            "new ExampleController('Sample')",
            $$"return new ExampleController('Sample', $container->get('my_module.sample_manager'));"
        )
    }

    fun testQuickFixUpdatesCreateMethodWithFullyQualifiedClassNameConstructor() {
        assertCreateMethodQuickFix(
            "new \\Drupal\\my_module\\Controller\\ExampleController('Sample')",
            $$"return new \\Drupal\\my_module\\Controller\\ExampleController('Sample', $container->get('my_module.sample_manager'));"
        )
    }

    private fun assertCreateMethodQuickFix(constructorCall: String, expectedReturn: String) {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addContainerInterface()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              private string $label;

              public function __construct(string $label) {
                $this->label = $label;
              }

              public static function create(\Symfony\Component\DependencyInjection\ContainerInterface $container): static {
                return $$constructorCall;
              }

              public function build(): array {
                $label = \Drupal::service('my_module.sample_<caret>manager')->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single {
            it.text == "Inject service 'my_module.sample_manager' into constructor"
        }
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertContainsText(result, "use Drupal\\my_module\\SampleManager;")
        assertContainsText(result, $$"protected SampleManager $sampleManager;")
        assertContainsText(result, $$"__construct(string $label, SampleManager $sample_manager)")
        assertContainsText(result, $$"$this->sampleManager = $sample_manager;")
        assertContainsText(result, expectedReturn)
        assertContainsText(result, $$"$label = $this->sampleManager->label();")
    }

    private fun addDrupalClass() {
        myFixture.addFileToProject(
            "core/lib/Drupal.php",
            $$"""
            <?php

            class Drupal {
              public static function service(string $id): object {}
              public static function routeMatch(): \Drupal\Core\Routing\RouteMatchInterface {}
              public static function currentUser(): \Drupal\Core\Session\AccountProxyInterface {}
            }
            """.trimIndent()
        )
    }

    private fun addDrupalCoreInterfaces() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Routing/RouteMatchInterface.php",
            $$"""
            <?php

            namespace Drupal\Core\Routing;

            interface RouteMatchInterface {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Session/AccountProxyInterface.php",
            $$"""
            <?php

            namespace Drupal\Core\Session;

            interface AccountProxyInterface {}
            """.trimIndent()
        )
    }

    private fun addContainerInterface() {
        myFixture.addFileToProject(
            "vendor/symfony/dependency-injection/ContainerInterface.php",
            $$"""
            <?php

            namespace Symfony\Component\DependencyInjection;

            interface ContainerInterface {
              public function get(string $id): object;
            }
            """.trimIndent()
        )
    }

    private fun addControllerBaseClass() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/DependencyInjection/ContainerInjectionInterface.php",
            $$"""
            <?php

            namespace Drupal\Core\DependencyInjection;

            use Symfony\Component\DependencyInjection\ContainerInterface;

            interface ContainerInjectionInterface {
              public static function create(ContainerInterface $container): static;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Controller/ControllerBase.php",
            $$"""
            <?php

            namespace Drupal\Core\Controller;

            use Drupal\Core\DependencyInjection\ContainerInjectionInterface;
            use Symfony\Component\DependencyInjection\ContainerInterface;

            abstract class ControllerBase implements ContainerInjectionInterface {
              public static function create(ContainerInterface $container): static {}
            }
            """.trimIndent()
        )
    }

    private fun addSampleService() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/SampleManager.php",
            """
            <?php

            namespace Drupal\my_module;

            class SampleManager {
              public function label(): string {
                return 'Sample';
              }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              my_module.sample_manager:
                class: Drupal\my_module\SampleManager
            """.trimIndent()
        )
    }

    private fun assertContainsText(text: String, expected: String) {
        assertTrue(
            "Expected to find:\n$expected\n\nIn:\n$text",
            text.contains(expected)
        )
    }
}
