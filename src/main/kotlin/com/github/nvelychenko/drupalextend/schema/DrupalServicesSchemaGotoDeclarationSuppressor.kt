package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor

class DrupalServicesSchemaGotoDeclarationSuppressor : JsonSchemaGotoDeclarationSuppressor {
    override fun shouldSuppressGtd(element: PsiElement): Boolean {
        val project = element.project
        if (!Services.isSupportEnabled(project)) return false

        return ServicesYaml.serviceDefinitionFromKeyElement(element) != null
    }
}
