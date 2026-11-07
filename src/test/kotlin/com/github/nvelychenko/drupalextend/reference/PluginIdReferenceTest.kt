package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.PhpClass

class PluginIdReferenceTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    fun testRestResourcePluginIdNavigatesToPluginClass() {
        addAddressResource()
        myFixture.configureByText(
            "rest.resource.nll_core_verify_address.yml",
            """
            id: nll_core_verify_address
            plugin_id: nll_core_verify_<caret>address
            granularity: resource
            """.trimIndent()
        )

        assertEquals("AddressResource", (myFixture.elementAtCaret as PhpClass).name)
    }

    fun testViewsHandlerPluginIdsNavigateToPluginClasses() {
        myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/views/field/ApplicationFundedAmount.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\field;

            use Drupal\views\Attribute\ViewsField;

            #[ViewsField(
              'node_loan_funded_amount',
            )]
            class ApplicationFundedAmount {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "views.view.applications.yml",
            """
            id: applications
            display:
              default:
                display_plugin: default
                display_options:
                  fields:
                    loan_funded_amount:
                      id: loan_funded_amount
                      plugin_id: node_loan_funded_<caret>amount
            """.trimIndent()
        )

        assertEquals("ApplicationFundedAmount", (myFixture.elementAtCaret as PhpClass).name)
    }

    fun testDerivedBlockPluginIdNavigatesToDeriverBaseClass() {
        myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/Block/NllMenuBlock.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\Block;

            use Drupal\Core\Block\Attribute\Block;
            use Drupal\Core\StringTranslation\TranslatableMarkup;

            #[Block(
              id: 'nll_menu_block',
              admin_label: new TranslatableMarkup('NLL Menu'),
            )]
            class NllMenuBlock {}
            """.trimIndent()
        )
        myFixture.configureByText(
            "block.block.account_menu.yml",
            """
            id: account_menu
            region: header
            plugin: 'nll_menu_block:<caret>account'
            """.trimIndent()
        )

        assertEquals("NllMenuBlock", (myFixture.elementAtCaret as PhpClass).name)
    }

    fun testFindUsagesOnRestResourceClassListsEnablingConfig() {
        val file = addAddressResource()
        myFixture.addFileToProject(
            "config/sync/rest.resource.nll_core_verify_address.yml",
            """
            id: nll_core_verify_address
            plugin_id: nll_core_verify_address
            granularity: resource
            """.trimIndent()
        )

        val usages = myFixture.findUsages(file.phpClass("AddressResource"))

        assertTrue(
            "Expected the enabling rest.resource config to be listed as a usage",
            usages.any { it.file?.name == "rest.resource.nll_core_verify_address.yml" }
        )
    }

    fun testFindUsagesOnViewsFilterClassListsViewsConfig() {
        val file = myFixture.addFileToProject(
            "modules/custom/nll_core/src/Plugin/views/filter/PreApprovedDateFilter.php",
            """
            <?php

            namespace Drupal\nll_core\Plugin\views\filter;

            use Drupal\views\Attribute\ViewsFilter;

            #[ViewsFilter(
              'pre_approved_date_filter',
            )]
            class PreApprovedDateFilter {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "config/sync/views.view.applications.yml",
            """
            id: applications
            display:
              default:
                display_plugin: default
                display_options:
                  filters:
                    pre_approved_date:
                      id: pre_approved_date
                      plugin_id: pre_approved_date_filter
            """.trimIndent()
        )

        val usages = myFixture.findUsages(file.phpClass("PreApprovedDateFilter"))

        assertTrue(
            "Expected the views config to be listed as a usage",
            usages.any { it.file?.name == "views.view.applications.yml" }
        )
    }

    /**
     * The user-facing default Find Usages scope is "Project Files", which does NOT cover an
     * excluded config directory that is only indexed through the synthetic config library —
     * the search must widen itself by the config directories or the enabling config line
     * silently disappears from the results.
     */
    fun testFindUsagesCoversExcludedConfigDirectoryUnderProjectFilesScope() {
        myFixture.addFileToProject(
            "config/default/core.extension.yml",
            """
            module:
              rest: 0
            """.trimIndent()
        )
        val configFile = myFixture.addFileToProject(
            "config/default/rest.resource.nll_core_verify_address.yml",
            """
            id: nll_core_verify_address
            plugin_id: nll_core_verify_address
            granularity: resource
            """.trimIndent()
        )
        val file = addAddressResource()

        val configDir = myFixture.findFileInTempDir("config")
        PsiTestUtil.addExcludedRoot(myFixture.module, configDir)
        try {
            // Premise guard: the excluded config file really is outside "Project Files" —
            // otherwise this test would pass without the scope-widening under test.
            assertFalse(GlobalSearchScope.projectScope(project).contains(configFile.virtualFile))

            val references = ReferencesSearch
                .search(file.phpClass("AddressResource"), GlobalSearchScope.projectScope(project))
                .findAll()

            assertTrue(
                "Expected the enabling config in the excluded directory to surface as a usage",
                references.any { it.element.containingFile?.name == "rest.resource.nll_core_verify_address.yml" }
            )
        } finally {
            PsiTestUtil.removeExcludedRoot(myFixture.module, configDir)
        }
    }

    fun testRenamingPluginClassDoesNotRewriteThePluginIdInConfig() {
        val file = addAddressResource()
        val config = myFixture.addFileToProject(
            "config/sync/rest.resource.nll_core_verify_address.yml",
            """
            id: nll_core_verify_address
            plugin_id: nll_core_verify_address
            granularity: resource
            """.trimIndent()
        )

        myFixture.renameElement(file.phpClass("AddressResource"), "VerifyAddressResource")

        // The scalar is the plugin id, not the class name — it must survive the rename intact.
        assertTrue(config.text.contains("plugin_id: nll_core_verify_address"))
        assertFalse(config.text.contains("VerifyAddressResource"))
    }

    fun testUnknownPluginIdResolvesToNothingWithoutErrors() {
        myFixture.configureByText(
            "rest.resource.nll_core_unknown.yml",
            """
            id: nll_core_unknown
            plugin_id: nll_core_<caret>unknown
            """.trimIndent()
        )

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Plugin id position must carry a reference", reference)
        assertNull("Unknown plugin id must resolve to nothing (soft reference)", reference!!.resolve())
    }

    private fun addAddressResource() = myFixture.addFileToProject(
        "modules/custom/nll_core/src/Plugin/rest/resource/AddressResource.php",
        """
        <?php

        namespace Drupal\nll_core\Plugin\rest\resource;

        use Drupal\Core\StringTranslation\TranslatableMarkup;
        use Drupal\rest\Attribute\RestResource;

        #[RestResource(
          id: 'nll_core_verify_address',
          label: new TranslatableMarkup('NLL Verify address'),
        )]
        class AddressResource {}
        """.trimIndent()
    )

    private fun com.intellij.psi.PsiFile.phpClass(name: String): PhpClass {
        return PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .firstOrNull { it.name == name }
            ?: error("Expected PHP class $name")
    }
}
