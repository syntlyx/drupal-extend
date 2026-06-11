package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspection
import com.github.nvelychenko.drupalextend.project.DrupalCodeStyleSeverity
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DrupalCodeStyleSeverityTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.drupalExtendSettings.isEnabled = true
        project.drupalExtendSettings.isCodeStyleLinterEnabled = true
        // The light fixture shares the project (and thus the settings service) across tests
        // in this class — pin the severities the assertions depend on.
        project.drupalExtendSettings.codeStyleStringQuotesSeverity = DrupalCodeStyleSeverity.WEAK_WARNING
        project.drupalExtendSettings.codeStyleElsePlacementSeverity = DrupalCodeStyleSeverity.WEAK_WARNING
    }

    fun testRuleSeverityErrorRendersAsError() {
        project.drupalExtendSettings.codeStyleStringQuotesSeverity = DrupalCodeStyleSeverity.ERROR
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText("Example.php", "<?php\n\$x = \"hello\";")

        val highlight = myFixture.doHighlighting()
            .single { it.description == "Prefer single quotes for strings without interpolation or escapes" }

        assertEquals(HighlightSeverity.ERROR, highlight.severity)
    }

    fun testRuleSeverityWarningRendersAsWarning() {
        project.drupalExtendSettings.codeStyleStringQuotesSeverity = DrupalCodeStyleSeverity.WARNING
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText("Example.php", "<?php\n\$x = \"hello\";")

        val highlight = myFixture.doHighlighting()
            .single { it.description == "Prefer single quotes for strings without interpolation or escapes" }

        assertEquals(HighlightSeverity.WARNING, highlight.severity)
    }

    fun testDefaultSeverityIsWeakWarning() {
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText("Example.php", "<?php\n\$x = \"hello\";")

        val highlight = myFixture.doHighlighting()
            .single { it.description == "Prefer single quotes for strings without interpolation or escapes" }

        assertEquals(HighlightSeverity.WEAK_WARNING, highlight.severity)
    }

    fun testSeverityOffDisablesTheRule() {
        project.drupalExtendSettings.codeStyleStringQuotesSeverity = DrupalCodeStyleSeverity.OFF
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText("Example.php", "<?php\n\$x = \"hello\";")

        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }

        assertFalse(descriptions.contains("Prefer single quotes for strings without interpolation or escapes"))
    }

    fun testBooleanBridgeMapsToSeverity() {
        val settings = project.drupalExtendSettings

        settings.isCodeStyleStringQuotesEnabled = false
        assertEquals(DrupalCodeStyleSeverity.OFF, settings.codeStyleStringQuotesSeverity)
        assertFalse(settings.isCodeStyleStringQuotesEnabled)

        settings.isCodeStyleStringQuotesEnabled = true
        assertEquals(DrupalCodeStyleSeverity.WEAK_WARNING, settings.codeStyleStringQuotesSeverity)
        assertTrue(settings.isCodeStyleStringQuotesEnabled)

        // Discouraged functions default to WARNING — the bridge restores that default.
        settings.isCodeStyleDiscouragedFunctionsEnabled = true
        assertEquals(DrupalCodeStyleSeverity.WARNING, settings.codeStyleDiscouragedFunctionsSeverity)
    }

    fun testInspectionParticipatesInCodeCleanup() {
        // The marker interface routes our quick fixes through Code -> Code Cleanup and the
        // commit dialog's "Cleanup code" checkbox.
        assertTrue(DrupalCodeStyleInspection() is com.intellij.codeInspection.CleanupLocalInspectionTool)
    }

    fun testSeverityAppliesOnlyToTheConfiguredRule() {
        // string_quotes raised to ERROR must not affect another rule's level.
        project.drupalExtendSettings.codeStyleStringQuotesSeverity = DrupalCodeStyleSeverity.ERROR
        myFixture.enableInspections(DrupalCodeStyleInspection::class.java)
        myFixture.configureByText("Example.php", "<?php\n\$x = \"hello\";\nif (\$x) {\n} else {\n}")

        val elsePlacement = myFixture.doHighlighting()
            .single { it.description == "else/elseif must start on a new line" }

        assertEquals(HighlightSeverity.WEAK_WARNING, elsePlacement.severity)
    }
}
