package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl

class ServicesIndexTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isServicesCompletionEnabled = true
    }

    fun testFqnServiceIdWithoutClassKeyFallsBackToIdAsClass() {
        val psiFile = myFixture.addFileToProject(
            "modules/custom/my_module/my_module.services.yml",
            """
            services:
              _defaults:
                autowire: true
              Drupal\my_module\SampleManager: ~
              my_module.classic:
                class: Drupal\my_module\Classic
              my_module.no_class:
                tags:
                  - { name: event_subscriber }
            """.trimIndent()
        )

        val map = ServicesIndex().indexer.map(FileContentImpl.createByFile(psiFile.virtualFile, project))

        // Modern autowired style: the id IS the FQCN.
        assertEquals("Drupal\\my_module\\SampleManager", map["Drupal\\my_module\\SampleManager"])
        assertEquals("Drupal\\my_module\\Classic", map["my_module.classic"])
        // Non-FQN id without a class stays unresolved.
        assertEquals("", map["my_module.no_class"])
        // `_defaults` is not a service.
        assertFalse(map.containsKey("_defaults"))
    }
}
