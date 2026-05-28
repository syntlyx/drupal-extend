@file:Suppress("UnstableApiUsage")

package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLFile

class DrupalYamlTargetPresentationProvider : GotoTargetPresentationProvider {
    override fun getTargetPresentation(element: PsiElement, hasDifferentNames: Boolean): TargetPresentation? {
        return ReadAction.compute<TargetPresentation?, RuntimeException> {
            if (!element.isDrupalYamlNavigationTarget()) return@compute null

            val file = element.containingFile ?: return@compute null
            val lineNumber = element.navigationLineNumber() ?: return@compute null
            val codePreview = element.navigationCodePreview()

            TargetPresentation
                .builder(file.name)
                .icon(file.fileType.icon)
                .containerText(lineNumber.toString())
                .let { builder ->
                    if (codePreview == null) builder else builder.locationText(codePreview)
                }
                .presentation()
        }
    }
}

internal fun PsiElement.isDrupalYamlNavigationTarget(): Boolean {
    val file = containingFile as? YAMLFile ?: return false
    val fileName = file.virtualFile?.name ?: file.name

    return when {
        ServicesYaml.isServicesFileName(fileName) -> ServicesYaml.serviceDefinitionFromKeyElement(this) != null
        RoutingYaml.isRoutingFileName(fileName) -> RoutingYaml.routeDefinitionFromKeyElement(this) != null
        RoutingYaml.isLinksFileName(fileName) -> RoutingYaml.linkDefinitionFromKeyElement(this) != null
        fileName.endsWith(".permissions.yml") -> Permissions.permissionDefinitionFromKeyElement(this) != null
        else -> false
    }
}

internal fun PsiElement.navigationLineNumber(): Int? {
    val file = containingFile ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
    return document.getLineNumber(textRange.startOffset) + 1
}

internal fun PsiElement.navigationCodePreview(): String? {
    val file = containingFile ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
    val line = navigationLineNumber()?.minus(1) ?: return null
    return document.charsSequence
        .subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line))
        .toString()
        .trim()
        .replace(Regex("\\s+"), " ")
        .let { if (it.length <= 180) it else it.take(177) + "..." }
}
