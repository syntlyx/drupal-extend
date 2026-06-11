package com.github.nvelychenko.drupalextend.routes

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.inspection.RoutesPhpInspection
import com.github.nvelychenko.drupalextend.inspection.RoutesYamlInspection
import com.github.nvelychenko.drupalextend.documentation.RoutesYamlDocumentationProvider
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.DrupalUsageTargetProvider
import com.github.nvelychenko.drupalextend.reference.RoutesYamlGotoDeclarationHandler
import com.github.nvelychenko.drupalextend.schema.DrupalRoutesSchemaGotoDeclarationSuppressor
import com.github.nvelychenko.drupalextend.usage.DrupalImplicitUsageProvider
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

class RoutesYamlSupportTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isRoutesCompletionEnabled = true
    }

    fun testRoutingYamlHelperParsesRouteDefinitionsAndHandlers() {
        val yamlFile = myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application/{application}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
              requirements:
                _permission: 'access content'
            """.trimIndent()
        ) as YAMLFile

        val routeDefinition = RoutingYaml.routeDefinitions(yamlFile).single()
        val handler = RoutingYaml.routeHandler(routeDefinition)

        assertEquals("my_module.application_view", routeDefinition.keyText)
        assertEquals("/application/{application}", RoutingYaml.routePath(routeDefinition))
        assertEquals("Drupal\\my_module\\Controller\\ApplicationController", handler?.className)
        assertEquals("view", handler?.methodName)
    }

    fun testRoutesIndexStoresRouteNamesPathsAndHandlers() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application/{application}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
            """.trimIndent()
        )

        val route = FileBasedIndex.getInstance()
            .getValues(
                ROUTES_INDEX_KEY,
                "my_module.application_view",
                GlobalSearchScope.allScope(project)
            )
            .firstOrNull()

        assertNotNull(route)
        assertEquals("/application/{application}", route?.path)
        assertEquals("Drupal\\my_module\\Controller\\ApplicationController", route?.handlerClass)
        assertEquals("view", route?.handlerMethod)
    }

    fun testRouteDefinitionKeySuppressesJsonSchemaGotoDeclaration() {
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_<caret>view:
              path: '/application/{application}'
            """.trimIndent()
        )

        val suppressor = DrupalRoutesSchemaGotoDeclarationSuppressor()

        assertTrue(suppressor.shouldSuppressGtd(myFixture.elementAtCaret))
    }

    fun testRouteNestedKeyDoesNotSuppressJsonSchemaGotoDeclaration() {
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_view:
              pa<caret>th: '/application/{application}'
            """.trimIndent()
        )

        val suppressor = DrupalRoutesSchemaGotoDeclarationSuppressor()

        assertFalse(suppressor.shouldSuppressGtd(myFixture.elementAtCaret))
    }

    fun testRouteHandlersAreImplicitUsages() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ApplicationController.php",
            """
            <?php
            namespace Drupal\my_module\Controller;

            class ApplicationController {
              public function view() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
            """.trimIndent()
        )

        val phpClass = PsiTreeUtil.findChildOfType(classFile, PhpClass::class.java)
            ?: error("Expected PHP class")
        val method = phpClass.findMethodByName("view") ?: error("Expected view method")
        val usageProvider = DrupalImplicitUsageProvider()

        assertTrue(usageProvider.isImplicitUsage(phpClass))
        assertTrue(usageProvider.isImplicitUsage(method))
    }

    fun testRouteFormClassIsImplicitUsage() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Form/SettingsForm.php",
            """
            <?php
            namespace Drupal\my_module\Form;

            class SettingsForm {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.settings:
              path: '/settings'
              defaults:
                _form: '\Drupal\my_module\Form\SettingsForm'
            """.trimIndent()
        )

        val phpClass = PsiTreeUtil.findChildOfType(classFile, PhpClass::class.java)
            ?: error("Expected PHP class")

        assertTrue(DrupalImplicitUsageProvider().isImplicitUsage(phpClass))
    }

    fun testRouteCustomAccessMethodIsImplicitUsage() {
        val classFile = myFixture.addFileToProject(
            "modules/custom/my_module/src/Access/ApplicationAccess.php",
            """
            <?php
            namespace Drupal\my_module\Access;

            class ApplicationAccess {
              public function access() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application'
              requirements:
                _custom_access: '\Drupal\my_module\Access\ApplicationAccess::access'
            """.trimIndent()
        )

        val phpClass = PsiTreeUtil.findChildOfType(classFile, PhpClass::class.java)
            ?: error("Expected PHP class")
        val method = phpClass.findMethodByName("access") ?: error("Expected access method")
        val usageProvider = DrupalImplicitUsageProvider()

        assertTrue(usageProvider.isImplicitUsage(phpClass))
        assertTrue(usageProvider.isImplicitUsage(method))
    }

    fun testLinksIndexStoresLinkIdsAndTitles() {
        addMenuLinksFile()

        val title = FileBasedIndex.getInstance()
            .getValues(
                LINKS_INDEX_KEY,
                "system.admin_config",
                GlobalSearchScope.allScope(project)
            )
            .firstOrNull()

        assertEquals("Configuration", title)
    }

    fun testPhpRouteCompletionSuggestsIndexedRoutes() {
        addUrlClass()
        addRouteFile()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('my_module.app<caret>');
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                <?php

                \Drupal\Core\Url::fromRoute('my_module.application_view');
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "my_module.application_view")
    }

    fun testPhpRouteCompletionSuggestsSpecialRoutes() {
        addUrlClass()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('<cu<caret>');
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                <?php

                \Drupal\Core\Url::fromRoute('<current>');
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "<current>")
    }

    fun testPhpRouteReferenceResolvesToRouteDefinition() {
        addUrlClass()
        addRouteFile()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('my_module.application_<caret>view');
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("my_module.application_view", resolvedKey?.keyText)
        assertEquals("my_module.application_view", reference.rangeInElement.substring(reference.element.text))
    }

    fun testPhpRouteInspectionReportsUnknownRoute() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addUrlClass()
        addRouteFile()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('my_module.missing_route');
            \Drupal\Core\Url::fromRoute('my_module.application_view');
            \Drupal\Core\Url::fromRoute('<current>');
            \Drupal\Core\Url::fromRoute('view.reports.reports');
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Unknown route 'my_module.missing_route'")
        assertFalse(descriptions.contains("Unknown route 'my_module.application_view'"))
        assertFalse(descriptions.contains("Unknown route '<current>'"))
        assertFalse(descriptions.contains("Unknown route 'view.reports.reports'"))
    }

    fun testPhpRouteInspectionAcceptsDynamicEntityRoute() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addUrlClass()
        addNodeEntityClass()
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            \Drupal\Core\Url::fromRoute('entity.node.edit_form', ['node' => $node]);
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Unknown route 'entity.node.edit_form'"))
    }

    fun testPhpRouteInspectionAcceptsNodeAddRouteAlias() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addUrlClass()
        addNodeEntityClass()
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            \Drupal\Core\Url::fromRoute('node.add', ['node_type' => $node_type]);
            \Drupal\Core\Url::fromRoute('entity.node.add_form', ['node_type' => $node_type]);
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Unknown route 'node.add'"))
        assertFalse(descriptions.contains("Unknown route 'entity.node.add_form'"))
        assertFalse(descriptions.any { it.startsWith("Missing route parameter") })
    }

    fun testPhpRouteInspectionReportsMissingNodeAddRouteParameter() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addUrlClass()
        addNodeEntityClass()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('node.add');
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Missing route parameter 'node_type' for route 'node.add'")
    }

    fun testPhpRouteInspectionReportsMissingRouteParameter() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addFormStateInterface()
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.item_list:
              path: '/items/{id}'
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_<caret>list');
            }
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Missing route parameter 'id' for route 'my_module.item_list'")
    }

    fun testPhpRouteInspectionMissingRouteParameterQuickFixAddsParameterArray() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addFormStateInterface()
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.item_list:
              path: '/items/{id}'
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_list');
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single { it.text == "Add missing route parameters" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_list', ['id' => $id]);
            }
            """.trimIndent()
        )
    }

    fun testPhpRouteInspectionQuickFixPreservesMultilineArguments() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addFormStateInterface()
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.item_detail:
              path: '/items/{id}/{step}'
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect(
                'my_module.item_<caret>detail',
                [
                  // Existing formatting must survive the fix untouched.
                  'id' => $id,
                ]
              );
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single { it.text == "Add missing route parameters" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect(
                'my_module.item_detail',
                [
                  // Existing formatting must survive the fix untouched.
                  'id' => $id,
                  'step' => $step,
                ]
              );
            }
            """.trimIndent()
        )
    }

    fun testPhpRouteInspectionQuickFixExtendsLegacyArraySyntaxWithSpacing() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addFormStateInterface()
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.item_detail:
              path: '/items/{id}/{step}'
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_<caret>detail', array ('id' => $id));
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single { it.text == "Add missing route parameters" }
        myFixture.launchAction(intention)

        // `array (...)` with a space was a silent no-op before; the PSI-derived insertion handles it.
        myFixture.checkResult(
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_detail', array ('id' => $id, 'step' => $step));
            }
            """.trimIndent()
        )
    }

    fun testPhpRouteInspectionMissingRouteParameterQuickFixExtendsExistingParameterArray() {
        myFixture.enableInspections(RoutesPhpInspection::class.java)
        addFormStateInterface()
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.item_detail:
              path: '/items/{id}/{step}'
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.php",
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_<caret>detail', ['id' => $id]);
            }
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.getAllQuickFixes().single { it.text == "Add missing route parameters" }
        myFixture.launchAction(intention)

        myFixture.checkResult(
            $$"""
            <?php

            function submit(\Drupal\Core\Form\FormStateInterface $form_state): void {
              $form_state->setRedirect('my_module.item_detail', ['id' => $id, 'step' => $step]);
            }
            """.trimIndent()
        )
    }

    fun testLinkCreateFromRouteCompletionUsesSecondArgument() {
        addLinkClass()
        addRouteFile()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Link::createFromRoute('Open', 'my_module.app<caret>');
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                <?php

                \Drupal\Core\Link::createFromRoute('Open', 'my_module.application_view');
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "my_module.application_view")
    }

    fun testYamlRouteNameCompletionSuggestsIndexedRoutes() {
        addRouteFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: my_module.app<caret>
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                my_module.application:
                  title: Application
                  route_name: my_module.application_view
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "my_module.application_view")
    }

    fun testYamlRouteNameCompletionSuggestsSpecialRoutes() {
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: <fr<caret>
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                my_module.application:
                  title: Application
                  route_name: <front>
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "<front>")
    }

    fun testYamlRouteNameReferenceResolvesToRouteDefinition() {
        addRouteFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: my_module.application_<caret>view
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("my_module.application_view", resolvedKey?.keyText)
    }

    fun testYamlAppearsOnReferenceResolvesToRouteDefinition() {
        addRouteFile()
        myFixture.configureByText(
            "my_module.links.action.yml",
            """
            my_module.application.add:
              title: Add application
              route_name: my_module.application_link
              appears_on:
                - my_module.application_<caret>view
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("my_module.application_view", resolvedKey?.keyText)
    }

    fun testYamlMenuParentDoesNotResolveToRouteDefinition() {
        addRouteFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: my_module.application_view
              parent: system.admin_<caret>config
            """.trimIndent()
        )

        assertNull(myFixture.getReferenceAtCaretPositionWithAssertion().resolve())
    }

    fun testYamlMenuParentCompletionSuggestsLinks() {
        addMenuLinksFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: <front>
              parent: system.admin_<caret>
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                my_module.application:
                  title: Application
                  route_name: <front>
                  parent: system.admin_config
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "system.admin_config")
    }

    fun testYamlMenuParentReferenceResolvesToLinkDefinition() {
        addMenuLinksFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: <front>
              parent: system.admin_<caret>config
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("system.admin_config", resolvedKey?.keyText)
    }

    fun testYamlPermissionCompletionSuggestsIndexedPermissions() {
        addPermissionFile()
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application'
              requirements:
                _permission: access sample<caret>
            """.trimIndent()
        )

        val lookupElements = myFixture.complete(CompletionType.BASIC)
        if (lookupElements == null) {
            myFixture.checkResult(
                """
                my_module.application_view:
                  path: '/application'
                  requirements:
                    _permission: access sample content
                """.trimIndent()
            )
            return
        }

        assertContainsElements(lookupElements.map { it.lookupString }, "access sample content")
    }

    fun testYamlPermissionReferenceResolvesToPermissionDefinition() {
        addPermissionFile()
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application'
              requirements:
                _permission: access sample<caret> content
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("access sample content", resolvedKey?.keyText)
    }

    fun testRoutesYamlInspectionReportsMissingHandlerClassAndMethod() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ApplicationController.php",
            """
            <?php
            namespace Drupal\my_module\Controller;

            class ApplicationController {
              public function view() {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.broken_route:
              path: '/broken'
              defaults:
                _controller: '\Drupal\my_module\Controller\MissingController::view'
            my_module.broken_method:
              path: '/broken-method'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::missing'
            my_module.local_reference:
              path: '/local-reference'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
              options:
                _admin_route: TRUE
            my_module.route_ref:
              path: '/route-ref'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
              requirements:
                _permission: 'access content'
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Unknown route handler class 'Drupal\\my_module\\Controller\\MissingController'",
            "Unknown route handler method 'Drupal\\my_module\\Controller\\ApplicationController::missing'"
        )
    }

    fun testRoutesYamlInspectionReportsMissingControllerRouteParameter() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ItemController.php",
            """
            <?php
            namespace Drupal\my_module\Controller;

            class ItemController {
              public function view(): array {
                return [];
              }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.item_view:
              path: '/items/{item}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ItemController::view'
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, missingControllerParameterMessage("item", "my_module.item_view"))
    }

    fun testRoutesYamlInspectionAcceptsControllerRouteParameter() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ItemController.php",
            $$"""
            <?php
            namespace Drupal\my_module\Controller;

            class ItemController {
              public function view($item): array {
                return [];
              }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.item_view:
              path: '/items/{item}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ItemController::view'
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains(missingControllerParameterMessage("item", "my_module.item_view")))
    }

    fun testControllerRouteParameterGotoTargetsRouteDefinition() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.item_view:
              path: '/items/{item}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ItemController::view'
            """.trimIndent()
        )
        myFixture.configureByText(
            "ItemController.php",
            $$"""
            <?php
            namespace Drupal\my_module\Controller;

            class ItemController {
              public function view($it<caret>em): array {
                return [];
              }
            }
            """.trimIndent()
        )

        val targets = RoutesYamlGotoDeclarationHandler()
            .getGotoDeclarationTargets(myFixture.elementAtCaret, myFixture.caretOffset, myFixture.editor)
            ?: emptyArray()
        val resolvedKey = PsiTreeUtil.getParentOfType(targets.firstOrNull(), YAMLKeyValue::class.java, false)
        assertEquals("my_module.item_view", resolvedKey?.keyText)
    }

    fun testRoutesYamlInspectionReportsControllerRouteEntityTypeMismatch() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        addItemEntityClass()
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Entity/OtherItem.php",
            """
            <?php
            namespace Drupal\my_module\Entity;

            class OtherItem {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ItemController.php",
            $$"""
            <?php
            namespace Drupal\my_module\Controller;

            class ItemController {
              public function view(\Drupal\my_module\Entity\OtherItem $item): array {
                return [];
              }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.item_view:
              path: '/items/{item}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ItemController::view'
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(
            descriptions,
            "Route parameter 'item' expects 'Drupal\\my_module\\Entity\\Item', 'Drupal\\my_module\\Entity\\OtherItem' provided"
        )
    }

    fun testRoutesYamlInspectionAcceptsControllerRouteEntityType() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        addItemEntityClass()
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ItemController.php",
            $$"""
            <?php
            namespace Drupal\my_module\Controller;

            class ItemController {
              public function view(\Drupal\my_module\Entity\Item $item): array {
                return [];
              }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.item_view:
              path: '/items/{item}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ItemController::view'
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.any { it.startsWith("Route parameter 'item' expects") })
    }

    fun testRoutesYamlInspectionReportsUnknownRouteReference() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        addRouteFile()
        addNodeEntityClass()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: my_module.missing_route
            my_module.front:
              title: Front
              route_name: <front>
            my_module.node_add:
              title: Add content
              route_name: node.add
            my_module.view_reports:
              title: Reports
              route_name: view.reports.reports
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Unknown route 'my_module.missing_route'")
        assertFalse(descriptions.contains("Unknown route '<front>'"))
        assertFalse(descriptions.contains("Unknown route 'node.add'"))
        assertFalse(descriptions.contains("Unknown route 'view.reports.reports'"))
    }

    fun testRoutesYamlInspectionAcceptsLocalRouteReferences() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.base:
              path: '/base'
            my_module.child:
              path: '/child'
              base_route: my_module.base
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertFalse(descriptions.contains("Unknown route 'my_module.base'"))
    }

    fun testRoutesYamlInspectionReportsUnknownLinkParent() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        addMenuLinksFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: <front>
              parent: system.missing_parent
            my_module.settings:
              title: Settings
              route_name: <front>
              parent: system.admin_config
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Unknown link parent 'system.missing_parent'")
        assertFalse(descriptions.contains("Unknown link parent 'system.admin_config'"))
    }

    fun testYamlTaskParentIdReferenceResolvesToTaskDefinition() {
        myFixture.addFileToProject(
            "core/modules/node/node.links.task.yml",
            """
            entity.node.canonical:
              title: View
              route_name: entity.node.canonical
              base_route: entity.node.canonical
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.links.task.yml",
            """
            my_module.node.extra:
              title: Extra
              route_name: <front>
              base_route: entity.node.canonical
              parent_id: entity.node.<caret>canonical
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolvedKey = PsiTreeUtil.getParentOfType(reference.resolve(), YAMLKeyValue::class.java, false)
        assertEquals("entity.node.canonical", resolvedKey?.keyText)
    }

    fun testRoutesYamlInspectionReportsUnknownPermissionToken() {
        myFixture.enableInspections(RoutesYamlInspection::class.java)
        addPermissionFile()
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application:
              path: '/application'
              requirements:
                _permission: 'access sample content+administer missing content'
            """.trimIndent()
        )

        val descriptions = myFixture.doHighlighting()
            .mapNotNull { it.description }

        assertContainsElements(descriptions, "Unknown permission 'administer missing content'")
        assertFalse(descriptions.contains("Unknown permission 'access sample content'"))
    }

    fun testYamlRouteDocumentationShowsPathAndHandler() {
        addRouteFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: my_module.application_<caret>view
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = RoutesYamlDocumentationProvider()
        val quickInfo = provider.getQuickNavigateInfo(element, element) ?: ""
        val documentation = provider.generateDoc(element, element) ?: ""

        assertEquals("my_module.application_view -> /application/{application}", quickInfo)
        assertTrue(documentation, documentation.contains("route my_module.application_view"))
        assertTrue(documentation, documentation.contains("/application/{application}"))
        assertTrue(
            documentation,
            documentation.contains("Drupal\\my_module\\Controller\\ApplicationController::view")
        )
    }

    fun testYamlSpecialRouteDocumentationShowsDescription() {
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.current:
              title: Current
              route_name: <current<caret>>
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = RoutesYamlDocumentationProvider()
        val quickInfo = provider.getQuickNavigateInfo(element, element) ?: ""
        val documentation = provider.generateDoc(element, element) ?: ""

        assertEquals("<current> -> current route", quickInfo)
        assertTrue(documentation, documentation.contains("route &lt;current&gt;"))
        assertTrue(documentation, documentation.contains("currently matched route"))
    }

    fun testYamlLinkParentDocumentationShowsTitle() {
        addMenuLinksFile()
        myFixture.configureByText(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: <front>
              parent: system.admin_<caret>config
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = RoutesYamlDocumentationProvider()
        val quickInfo = provider.getQuickNavigateInfo(element, element) ?: ""
        val documentation = provider.generateDoc(element, element) ?: ""

        assertEquals("system.admin_config -> Configuration", quickInfo)
        assertTrue(documentation, documentation.contains("link system.admin_config"))
        assertTrue(documentation, documentation.contains("Configuration"))
    }

    fun testYamlPermissionDocumentationShowsTitle() {
        addPermissionFile()
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application:
              path: '/application'
              requirements:
                _permission: access sample<caret> content
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1) ?: error("Expected element at caret")
        val provider = RoutesYamlDocumentationProvider()
        val quickInfo = provider.getQuickNavigateInfo(element, element) ?: ""
        val documentation = provider.generateDoc(element, element) ?: ""

        assertEquals("access sample content -> Access sample content", quickInfo)
        assertTrue(documentation, documentation.contains("permission access sample content"))
        assertTrue(documentation, documentation.contains("Access sample content"))
    }

    fun testRoutingHandlerReferenceResolvesToControllerMethod() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Controller/ApplicationController.php",
            """
            <?php
            namespace Drupal\my_module\Controller;

            class ApplicationController {
              public function view() {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application/{application}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::vi<caret>ew'
            """.trimIndent()
        )

        val resolved = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()

        assertTrue(resolved is Method)
        assertEquals("view", (resolved as Method).name)
    }

    fun testRoutingCustomAccessReferenceResolvesToAccessMethodInRequirements() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Access/ApplicationAccess.php",
            """
            <?php
            namespace Drupal\my_module\Access;

            class ApplicationAccess {
              public function access() {}
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application/{application}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
              requirements:
                _custom_access: '\Drupal\my_module\Access\ApplicationAccess::ac<caret>cess'
            """.trimIndent()
        )

        val resolved = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()

        assertTrue(resolved is Method)
        assertEquals("access", (resolved as Method).name)
    }

    fun testFindUsagesFromRouteDefinitionKeyFindsYamlAndPhpReferences() {
        addUrlClass()
        myFixture.addFileToProject(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('my_module.application_view');
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.routing.yml",
            """
            my_module.application_<caret>view:
              path: '/application/{application}'
            my_module.application_link:
              path: '/application-link'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: my_module.application_view
            """.trimIndent()
        )

        val routeKey = PsiTreeUtil.getParentOfType(myFixture.elementAtCaret, YAMLKeyValue::class.java, false)
            ?.key
            ?: error("Expected route key")
        val usages = myFixture.findUsages(routeKey)
        val usageSummary = usages.joinToString("\n") {
            "${it.element?.containingFile?.name}: ${it.element?.text}"
        }

        assertTrue(
            usageSummary,
            usages.any {
                it.element?.containingFile?.name == "consumer.php" &&
                        it.element?.text?.contains("my_module.application_view") == true
            }
        )
        assertTrue(
            usageSummary,
            usages.any {
                it.element?.containingFile?.name == "my_module.links.menu.yml" &&
                        it.element?.text?.contains("my_module.application_view") == true
            }
        )
    }

    fun testRouteUsageTargetProviderNormalizesUsageToRouteDefinition() {
        addUrlClass()
        addRouteFile()
        myFixture.configureByText(
            "consumer.php",
            """
            <?php

            \Drupal\Core\Url::fromRoute('my_module.application_<caret>view');
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val targets = DrupalUsageTargetProvider().getTargets(reference.element)
        val targetSummary = targets.joinToString("\n") { it.presentation?.presentableText ?: it.name.orEmpty() }

        assertEquals(targetSummary, 1, targets.size)
        val targetElement = (targets.single() as PsiElement2UsageTargetAdapter).targetElement
        assertEquals(
            "my_module.application_view",
            PsiTreeUtil.getParentOfType(targetElement, YAMLKeyValue::class.java, false)?.keyText
        )
    }

    fun testFindUsagesFromLinkDefinitionKeyFindsParentReferences() {
        myFixture.addFileToProject(
            "my_module.links.menu.yml",
            """
            my_module.application:
              title: Application
              route_name: <front>
              parent: system.admin_config
            """.trimIndent()
        )
        myFixture.configureByText(
            "system.links.menu.yml",
            """
            system.admin_<caret>config:
              title: Configuration
              route_name: system.admin_config
            """.trimIndent()
        )

        val linkKey = PsiTreeUtil.getParentOfType(myFixture.elementAtCaret, YAMLKeyValue::class.java, false)
            ?.key
            ?: error("Expected link key")
        val usages = myFixture.findUsages(linkKey)
        val usageSummary = usages.joinToString("\n") {
            "${it.element?.containingFile?.name}: ${it.element?.text}"
        }

        assertTrue(
            usageSummary,
            usages.any {
                it.element?.containingFile?.name == "my_module.links.menu.yml" &&
                        it.element?.text?.contains("system.admin_config") == true
            }
        )
    }

    fun testFindUsagesFromPermissionDefinitionKeyFindsYamlPermissionReferences() {
        myFixture.addFileToProject(
            "my_module.routing.yml",
            """
            my_module.application:
              path: '/application'
              requirements:
                _permission: access sample content
            """.trimIndent()
        )
        myFixture.configureByText(
            "my_module.permissions.yml",
            """
            access sample<caret> content:
              title: Access sample content
            """.trimIndent()
        )

        val permissionKey = PsiTreeUtil.getParentOfType(myFixture.elementAtCaret, YAMLKeyValue::class.java, false)
            ?.key
            ?: error("Expected permission key")
        val usages = myFixture.findUsages(permissionKey)
        val usageSummary = usages.joinToString("\n") {
            "${it.element?.containingFile?.name}: ${it.element?.text}"
        }

        assertTrue(
            usageSummary,
            usages.any {
                it.element?.containingFile?.name == "my_module.routing.yml" &&
                        it.element?.text?.contains("access sample content") == true
            }
        )
    }

    private fun addRouteFile() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.routing.yml",
            """
            my_module.application_view:
              path: '/application/{application}'
              defaults:
                _controller: '\Drupal\my_module\Controller\ApplicationController::view'
            """.trimIndent()
        )
    }

    private fun addMenuLinksFile() {
        myFixture.addFileToProject(
            "core/modules/system/system.links.menu.yml",
            """
            system.admin_config:
              title: Configuration
              route_name: system.admin_config
            """.trimIndent()
        )
    }

    private fun addPermissionFile() {
        myFixture.addFileToProject(
            "modules/custom/my_module/my_module.permissions.yml",
            """
            access sample content:
              title: Access sample content
            """.trimIndent()
        )
    }

    private fun addUrlClass() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Url.php",
            $$"""
            <?php
            namespace Drupal\Core;

            class Url {
              public static function fromRoute(string $route_name): object {}
            }
            """.trimIndent()
        )
    }

    private fun addLinkClass() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Link.php",
            $$"""
            <?php
            namespace Drupal\Core;

            class Link {
              public static function createFromRoute(string $text, string $route_name, array $parameters = []): object {}
            }
            """.trimIndent()
        )
    }

    private fun addFormStateInterface() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Form/FormStateInterface.php",
            $$"""
            <?php
            namespace Drupal\Core\Form;

            interface FormStateInterface {
              public function setRedirect(string $route_name, array $route_parameters = []): void;
            }
            """.trimIndent()
        )
    }

    private fun addNodeEntityClass() {
        myFixture.addFileToProject(
            "core/modules/node/src/Entity/Node.php",
            """
            <?php
            namespace Drupal\node\Entity;

            /**
             * @ContentEntityType(
             *   id = "node",
             * )
             */
            class Node {}
            """.trimIndent()
        )
    }

    private fun addItemEntityClass() {
        myFixture.addFileToProject(
            "modules/custom/my_module/src/Entity/Item.php",
            """
            <?php
            namespace Drupal\my_module\Entity;

            /**
             * @ContentEntityType(
             *   id = "item",
             * )
             */
            class Item {}
            """.trimIndent()
        )
    }

    private fun missingControllerParameterMessage(parameterName: String, routeName: String): String {
        return "Missing controller parameter '" + '$' + parameterName + "' for route '$routeName'"
    }

    private fun PsiElement.lineNumber(): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
            ?: error("Expected document for ${containingFile.name}")

        return document.getLineNumber(textRange.startOffset) + 1
    }
}
