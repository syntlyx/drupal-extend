package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.reference.referenceType.PermissionsReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PermissionsReferenceProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
    }

    fun testSingleArgumentAllowedIfHasPermissionDoesNotProvideReferenceOrThrow() {
        addAccessResultClass()
        myFixture.configureByText(
            "example.php",
            $$"""
            <?php

            $result = \Drupal\Core\Access\AccessResult::allowedIfHasPermission('administer nodes');
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildOfType(myFixture.file, StringLiteralExpression::class.java)
        assertNotNull(literal)
        // The permission sits at argument index 1; a single-argument call must yield no
        // permission reference instead of indexing past the argument list.
        val permissionReferences = literal!!.references.filterIsInstance<PermissionsReference>()
        assertEmpty(permissionReferences)
    }

    fun testPermissionArgumentOfAllowedIfHasPermissionGetsReference() {
        addAccessResultClass()
        myFixture.configureByText(
            "example.php",
            $$"""
            <?php

            $result = \Drupal\Core\Access\AccessResult::allowedIfHasPermission($account, 'administer nodes');
            """.trimIndent()
        )

        val literal = PsiTreeUtil.findChildrenOfType(myFixture.file, StringLiteralExpression::class.java)
            .single { it.contents == "administer nodes" }
        val permissionReferences = literal.references.filterIsInstance<PermissionsReference>()
        assertSize(1, permissionReferences)
    }

    private fun addAccessResultClass() {
        myFixture.addFileToProject(
            "core/lib/Drupal/Core/Access/AccessResult.php",
            $$"""
            <?php

            namespace Drupal\Core\Access;

            class AccessResult {
              public static function allowedIfHasPermission($account, $permission) {}
            }
            """.trimIndent()
        )
    }
}
