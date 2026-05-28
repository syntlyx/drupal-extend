package com.github.nvelychenko.drupalextend.inspection.pluginattribute

import com.github.nvelychenko.drupalextend.project.DrupalCoreVersion
import com.github.nvelychenko.drupalextend.project.DrupalProjectVersion
import com.intellij.openapi.project.Project

internal data class DrupalPluginAttributeSpec(
    val annotationName: String,
    val attributeFqn: String,
    val minimumDrupalVersion: DrupalCoreVersion,
    val supportedArguments: Set<String>? = null,
)

internal object DrupalPluginAttributeRegistry {
    private val drupal102 = DrupalCoreVersion(10, 2, 0)
    private val drupal103 = DrupalCoreVersion(10, 3, 0)
    private val drupal110 = DrupalCoreVersion(11, 0, 0)

    private val specs = listOf(
        spec("Action", "\\Drupal\\Core\\Action\\Attribute\\Action"),
        spec("Archiver", "\\Drupal\\Core\\Archiver\\Attribute\\Archiver"),
        spec("Block", "\\Drupal\\Core\\Block\\Attribute\\Block"),
        spec("CKEditor5Plugin", "\\Drupal\\ckeditor5\\Attribute\\CKEditor5Plugin"),
        spec("Condition", "\\Drupal\\Core\\Condition\\Attribute\\Condition"),
        spec("ConfigEntityType", "\\Drupal\\Core\\Entity\\Attribute\\ConfigEntityType"),
        spec("Constraint", "\\Drupal\\Core\\Validation\\Attribute\\Constraint"),
        spec("ContentEntityType", "\\Drupal\\Core\\Entity\\Attribute\\ContentEntityType"),
        spec("DataType", "\\Drupal\\Core\\TypedData\\Attribute\\DataType"),
        spec("Editor", "\\Drupal\\editor\\Attribute\\Editor"),
        spec("EntityReferenceSelection", "\\Drupal\\Core\\Entity\\Attribute\\EntityReferenceSelection"),
        spec("EntityType", "\\Drupal\\Core\\Entity\\Attribute\\EntityType"),
        spec(
            "FieldFormatter",
            "\\Drupal\\Core\\Field\\Attribute\\FieldFormatter",
            drupal103,
            supportedArguments = setOf("id", "label", "description", "field_types", "weight", "deriver")
        ),
        spec(
            "FieldType",
            "\\Drupal\\Core\\Field\\Attribute\\FieldType",
            drupal103,
            supportedArguments = setOf(
                "id",
                "label",
                "description",
                "category",
                "default_widget",
                "default_formatter",
                "list_class",
                "constraints",
                "cardinality",
                "no_ui",
                "deriver"
            )
        ),
        spec(
            "FieldWidget",
            "\\Drupal\\Core\\Field\\Attribute\\FieldWidget",
            drupal103,
            supportedArguments = setOf("id", "label", "description", "field_types", "multiple_values", "weight", "deriver")
        ),
        spec("Filter", "\\Drupal\\filter\\Attribute\\Filter"),
        spec("FormElement", "\\Drupal\\Core\\Render\\Attribute\\FormElement"),
        spec("IconExtractor", "\\Drupal\\Core\\Theme\\Icon\\Attribute\\IconExtractor", drupal110),
        spec("ImageToolkit", "\\Drupal\\Core\\ImageToolkit\\Attribute\\ImageToolkit"),
        spec("ImageToolkitOperation", "\\Drupal\\Core\\ImageToolkit\\Attribute\\ImageToolkitOperation"),
        spec("Layout", "\\Drupal\\Core\\Layout\\Attribute\\Layout"),
        spec("Mail", "\\Drupal\\Core\\Mail\\Attribute\\Mail"),
        spec("MediaSource", "\\Drupal\\media\\Attribute\\MediaSource"),
        spec("MigrateDestination", "\\Drupal\\migrate\\Attribute\\MigrateDestination"),
        spec("MigrateProcess", "\\Drupal\\migrate\\Attribute\\MigrateProcess"),
        spec("MigrateSource", "\\Drupal\\migrate\\Attribute\\MigrateSource"),
        spec("OEmbedMediaSource", "\\Drupal\\media\\Attribute\\OEmbedMediaSource"),
        spec("QueueWorker", "\\Drupal\\Core\\Queue\\Attribute\\QueueWorker"),
        spec("RenderElement", "\\Drupal\\Core\\Render\\Attribute\\RenderElement"),
        spec("RestResource", "\\Drupal\\rest\\Attribute\\RestResource"),
        spec("SectionStorage", "\\Drupal\\layout_builder\\Attribute\\SectionStorage"),
        spec("ViewsAccess", "\\Drupal\\views\\Attribute\\ViewsAccess"),
        spec("ViewsArea", "\\Drupal\\views\\Attribute\\ViewsArea"),
        spec("ViewsArgument", "\\Drupal\\views\\Attribute\\ViewsArgument"),
        spec("ViewsArgumentDefault", "\\Drupal\\views\\Attribute\\ViewsArgumentDefault"),
        spec("ViewsArgumentValidator", "\\Drupal\\views\\Attribute\\ViewsArgumentValidator"),
        spec("ViewsCache", "\\Drupal\\views\\Attribute\\ViewsCache"),
        spec("ViewsDisplay", "\\Drupal\\views\\Attribute\\ViewsDisplay"),
        spec("ViewsDisplayExtender", "\\Drupal\\views\\Attribute\\ViewsDisplayExtender"),
        spec("ViewsExposedForm", "\\Drupal\\views\\Attribute\\ViewsExposedForm"),
        spec("ViewsField", "\\Drupal\\views\\Attribute\\ViewsField"),
        spec("ViewsFilter", "\\Drupal\\views\\Attribute\\ViewsFilter"),
        spec("ViewsJoin", "\\Drupal\\views\\Attribute\\ViewsJoin"),
        spec("ViewsPager", "\\Drupal\\views\\Attribute\\ViewsPager"),
        spec("ViewsQuery", "\\Drupal\\views\\Attribute\\ViewsQuery"),
        spec("ViewsRelationship", "\\Drupal\\views\\Attribute\\ViewsRelationship"),
        spec("ViewsRow", "\\Drupal\\views\\Attribute\\ViewsRow"),
        spec("ViewsSort", "\\Drupal\\views\\Attribute\\ViewsSort"),
        spec("ViewsStyle", "\\Drupal\\views\\Attribute\\ViewsStyle"),
        spec("ViewsWizard", "\\Drupal\\views\\Attribute\\ViewsWizard"),
    )

    private val specsByAnnotationName = specs.associateBy { it.annotationName }

    fun supportedSpecs(project: Project): List<DrupalPluginAttributeSpec> {
        return specs.filter { DrupalProjectVersion.isAtLeast(project, it.minimumDrupalVersion) }
    }

    fun find(annotationName: String, project: Project): DrupalPluginAttributeSpec? {
        val spec = specsByAnnotationName[annotationName] ?: return null
        return spec.takeIf { DrupalProjectVersion.isAtLeast(project, it.minimumDrupalVersion) }
    }

    private fun spec(
        annotationName: String,
        attributeFqn: String,
        minimumDrupalVersion: DrupalCoreVersion = drupal102,
        supportedArguments: Set<String>? = null,
    ): DrupalPluginAttributeSpec {
        return DrupalPluginAttributeSpec(annotationName, attributeFqn, minimumDrupalVersion, supportedArguments)
    }
}

internal object DrupalNestedAnnotationRegistry {
    private val specs = mapOf(
        "ContextDefinition" to "\\Drupal\\Core\\Plugin\\Context\\ContextDefinition",
        "CKEditor5AspectsOfCKEditor5Plugin" to "\\Drupal\\ckeditor5\\Attribute\\CKEditor5AspectsOfCKEditor5Plugin",
        "DrupalAspectsOfCKEditor5Plugin" to "\\Drupal\\ckeditor5\\Attribute\\DrupalAspectsOfCKEditor5Plugin",
    )

    fun findClassFqn(annotationName: String): String? {
        return specs[annotationName]
    }
}
