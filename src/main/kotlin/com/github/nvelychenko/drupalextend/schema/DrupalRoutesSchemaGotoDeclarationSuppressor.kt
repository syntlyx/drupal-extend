package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor

class DrupalRoutesSchemaGotoDeclarationSuppressor : JsonSchemaGotoDeclarationSuppressor {
    override fun shouldSuppressGtd(element: PsiElement): Boolean {
        val project = element.project
        if (!Routes.isSupportEnabled(project)) return false

        return RoutingYaml.routeDefinitionFromKeyElement(element) != null
    }
}
