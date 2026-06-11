package com.github.nvelychenko.drupalextend.schema

import com.github.nvelychenko.drupalextend.util.Permissions
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor

class DrupalPermissionsSchemaGotoDeclarationSuppressor : JsonSchemaGotoDeclarationSuppressor {
    override fun shouldSuppressGtd(element: PsiElement): Boolean {
        val project = element.project
        if (!Permissions.isSupportEnabled(project)) return false

        return Permissions.permissionDefinitionFromKeyElement(element) != null
    }
}
