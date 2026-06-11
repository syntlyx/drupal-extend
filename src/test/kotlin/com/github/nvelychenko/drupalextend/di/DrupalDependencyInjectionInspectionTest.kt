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

    fun testQuickFixKeepsTraitUseFirstWhenInjectingNewConstructor() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        addStringTranslationTrait()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            use Drupal\Core\StringTranslation\StringTranslationTrait;

            class ExampleController {
              use StringTranslationTrait;

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
        val traitOffset = result.indexOf("use StringTranslationTrait;")
        val propertyOffset = result.indexOf($$"protected SampleManager $sampleManager;")
        val constructorOffset = result.indexOf("public function __construct(")
        assertTrue("Expected trait use to remain present", traitOffset >= 0)
        assertTrue("Expected injected property to be present", propertyOffset >= 0)
        assertTrue("Expected injected constructor to be present", constructorOffset >= 0)
        assertTrue("Expected trait use to stay before the injected property", traitOffset < propertyOffset)
        assertTrue("Expected trait use to stay before the injected constructor", traitOffset < constructorOffset)
    }

    fun testQuickFixAddsPromotedParameterToExistingPromotedConstructor() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        addFooService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              public function __construct(private Foo $foo) {}

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
        assertContainsText(result, $$"__construct(private Foo $foo, protected SampleManager $sampleManager)")
        assertContainsText(result, $$"$label = $this->sampleManager->label();")
        assertFalse(
            "Promoted constructor must not get a separate property field",
            result.contains($$"protected SampleManager $sampleManager;")
        )
        assertFalse(
            "Promoted constructor must not get a separate assignment",
            result.contains($$"$this->sampleManager = $sample_manager;")
        )
    }

    fun testQuickFixAppendsParameterToMultilineClassicConstructorWithTrailingComma() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            class ExampleController {
              protected string $label;

              public function __construct(
                string $label,
              ) {
                $this->label = $label;
              }

              public function build(): array {
                $value = \Drupal::service('my_module.sample_<caret>manager')->label();
                return ['#markup' => $value];
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
        assertFalse("Must not produce a double comma", result.contains(",,"))
        assertContainsText(result, $$"protected SampleManager $sampleManager;")
        assertContainsText(result, $$"string $label,")
        assertContainsText(result, $$"SampleManager $sample_manager,")
        assertContainsText(result, $$"$this->sampleManager = $sample_manager;")
        assertContainsText(result, $$"$value = $this->sampleManager->label();")
    }

    fun testQuickFixPlacesMembersInBodyWhenClassAttributeContainsBrace() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addSampleService()
        myFixture.configureByText(
            "ExampleController.php",
            $$"""
            <?php

            namespace Drupal\my_module\Controller;

            #[\Some\Attr('{x}')]
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
        // The injected members must land inside the class body, never inside the attribute.
        val attributeOffset = result.indexOf("#[\\Some\\Attr('{x}')]")
        val classBraceOffset = result.indexOf("class ExampleController {")
        val propertyOffset = result.indexOf($$"protected SampleManager $sampleManager;")
        val constructorOffset = result.indexOf("public function __construct(")
        assertTrue("Expected attribute to remain present", attributeOffset >= 0)
        assertTrue("Expected class declaration to remain present", classBraceOffset >= 0)
        assertTrue("Expected injected property to be present", propertyOffset >= 0)
        assertTrue("Expected injected constructor to be present", constructorOffset >= 0)
        assertTrue("Property must be inside the class body, after the opening brace", propertyOffset > classBraceOffset)
        assertTrue("Constructor must be inside the class body, after the opening brace", constructorOffset > classBraceOffset)
        // The attribute must be left untouched (no member text injected into it).
        assertContainsText(result, "#[\\Some\\Attr('{x}')]\nclass ExampleController {")
    }

    fun testQuickFixGeneratesCreateForExistingParameterlessConstructor() {
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
              public function __construct() {
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
        assertContainsText(result, "use Symfony\\Component\\DependencyInjection\\ContainerInterface;")
        assertContainsText(result, $$"public function __construct(SampleManager $sample_manager) {")
        assertContainsText(result, "public static function create(ContainerInterface \$container): static {")
        assertContainsText(result, "return new static(")
        assertContainsText(result, $$"$container->get('my_module.sample_manager'),")
        // Parameterless constructor → clean create(), no TODO placeholders.
        assertFalse("Parameterless constructor must not produce a TODO", result.contains("TODO"))
        assertFalse("Parameterless constructor must not produce a null placeholder", result.contains("null /*"))
        assertContainsText(result, $$"$label = $this->sampleManager->label();")
    }

    fun testQuickFixGeneratesCreateWithTodoForExistingConstructorWithParam() {
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
              protected string $label;

              public function __construct(string $label) {
                $this->label = $label;
              }

              public function build(): array {
                $value = \Drupal::service('my_module.sample_<caret>manager')->label();
                return ['#markup' => $value];
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
        assertContainsText(result, $$"__construct(string $label, SampleManager $sample_manager) {")
        assertContainsText(result, "public static function create(ContainerInterface \$container): static {")
        assertContainsText(result, "// TODO: provide the existing constructor arguments through the container.")
        assertContainsText(result, $$"null /* TODO: provide $label */,")
        assertContainsText(result, $$"$container->get('my_module.sample_manager'),")
        assertContainsText(result, $$"$value = $this->sampleManager->label();")
    }

    fun testQuickFixUsesAutowireAttributeInsteadOfCreateForAutowiredBaseClass() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addAutowireTrait()
        addAutowireAttributeClass()
        addAutowiredControllerBaseClass()
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
            use Symfony\Component\DependencyInjection\Attribute\Autowire;

            class ExampleController extends ControllerBase {
              protected SampleManager $sampleManager;

              public function __construct(#[Autowire(service: 'my_module.sample_manager')] SampleManager $sample_manager) {
                $this->sampleManager = $sample_manager;
              }

              public function build(): array {
                $label = $this->sampleManager->label();
                return ['#markup' => $label];
              }
            }
            """.trimIndent()
        )
    }

    fun testQuickFixAddsAutowireAttributeToPromotedConstructorWithDirectTraitUse() {
        myFixture.enableInspections(DrupalDependencyInjectionInspection::class.java)
        addDrupalClass()
        addAutowireTrait()
        addAutowireAttributeClass()
        addSampleService()
        addFooService()
        myFixture.configureByText(
            "ExampleService.php",
            $$"""
            <?php

            namespace Drupal\my_module;

            use Drupal\Core\DependencyInjection\AutowireTrait;

            class ExampleService {
              use AutowireTrait;

              public function __construct(private Foo $foo) {}

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
        assertContainsText(result, "use Symfony\\Component\\DependencyInjection\\Attribute\\Autowire;")
        assertContainsText(
            result,
            $$"__construct(private Foo $foo, #[Autowire(service: 'my_module.sample_manager')] protected SampleManager $sampleManager)"
        )
        assertContainsText(result, $$"$label = $this->sampleManager->label();")
        assertFalse(
            "Autowired class must not get a create() override",
            result.contains("public static function create(")
        )
    }

    fun testQuickFixAddsTodoToIndirectCreateBody() {
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
              protected string $label;

              public function __construct(string $label) {
                $this->label = $label;
              }

              public static function create(\Symfony\Component\DependencyInjection\ContainerInterface $container): static {
                $instance = new static('Sample');
                return $instance;
              }

              public function build(): array {
                $value = \Drupal::service('my_module.sample_<caret>manager')->label();
                return ['#markup' => $value];
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
        assertContainsText(result, $$"__construct(string $label, SampleManager $sample_manager) {")
        assertContainsText(
            result,
            $$"// TODO: pass $container->get('my_module.sample_manager') to the constructor call below."
        )
        // The indirect construction body must be left untouched.
        assertContainsText(result, $$"$instance = new static('Sample');")
        assertContainsText(result, $$"return $instance;")
        assertContainsText(result, $$"$value = $this->sampleManager->label();")
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

    private fun addAutowireTrait() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/DependencyInjection/AutowireTrait.php",
            $$"""
            <?php

            namespace Drupal\Core\DependencyInjection;

            use Symfony\Component\DependencyInjection\ContainerInterface;

            trait AutowireTrait {
              public static function create(ContainerInterface $container): static {}
            }
            """.trimIndent()
        )
    }

    private fun addAutowireAttributeClass() {
        myFixture.addFileToProject(
            "vendor/symfony/dependency-injection/Attribute/Autowire.php",
            $$"""
            <?php

            namespace Symfony\Component\DependencyInjection\Attribute;

            #[\Attribute(\Attribute::TARGET_PARAMETER)]
            class Autowire {
              public function __construct(mixed $value = null, ?string $service = null) {}
            }
            """.trimIndent()
        )
    }

    private fun addAutowiredControllerBaseClass() {
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

            use Drupal\Core\DependencyInjection\AutowireTrait;
            use Drupal\Core\DependencyInjection\ContainerInjectionInterface;

            abstract class ControllerBase implements ContainerInjectionInterface {
              use AutowireTrait;
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

    private fun addStringTranslationTrait() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/StringTranslation/StringTranslationTrait.php",
            $$"""
            <?php

            namespace Drupal\Core\StringTranslation;

            trait StringTranslationTrait {}
            """.trimIndent()
        )
    }

    private fun addFooService() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Foo.php",
            $$"""
            <?php

            namespace Drupal\my_module;

            class Foo {}
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
