package com.github.nvelychenko.drupalextend.reference.referenceProvider

import com.github.nvelychenko.drupalextend.extensions.keyPath
import com.github.nvelychenko.drupalextend.reference.referenceType.LinkReference
import com.github.nvelychenko.drupalextend.reference.referenceType.PermissionsReference
import com.github.nvelychenko.drupalextend.reference.referenceType.RouteHandlerReference
import com.github.nvelychenko.drupalextend.reference.referenceType.RouteReference
import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.Routes
import com.github.nvelychenko.drupalextend.util.RoutingYaml
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class RoutesYamlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val project = element.project

        if (!Routes.isSupportEnabled(project)) return PsiReference.EMPTY_ARRAY

        if (!RoutingYaml.isRouteReferenceFileName(element.containingFile?.virtualFile?.name)) {
            return PsiReference.EMPTY_ARRAY
        }

        val scalar = element as? YAMLScalar
            ?: PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java, false)
            ?: return PsiReference.EMPTY_ARRAY

        if (RoutingYaml.isKey(scalar)) {
            return PsiReference.EMPTY_ARRAY
        }

        getRouteReference(scalar)?.let { return arrayOf(it) }
        getLinkParentReference(scalar)?.let { return arrayOf(it) }
        getPermissionReferences(scalar).takeIf { it.isNotEmpty() }?.let { return it.toTypedArray() }
        getHandlerReference(scalar)?.let { return arrayOf(it) }

        return PsiReference.EMPTY_ARRAY
    }

    private fun getRouteReference(scalar: YAMLScalar): PsiReference? {
        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return null
        if (!RoutingYaml.isRouteReferenceKeyPath(keyPath)) return null

        val routeName = scalar.textValue.trim()
        if (routeName.isBlank()) return null

        return RouteReference(scalar, routeName)
    }

    private fun getLinkParentReference(scalar: YAMLScalar): PsiReference? {
        if (!RoutingYaml.isLinksFileName(scalar.containingFile.virtualFile?.name)) return null

        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return null
        if (!RoutingYaml.isLinkParentKeyPath(keyPath)) return null

        val linkId = scalar.textValue.trim()
        if (linkId.isBlank()) return null

        return LinkReference(scalar, linkId)
    }

    private fun getPermissionReferences(scalar: YAMLScalar): List<PsiReference> {
        if (!Permissions.isYamlPermissionReferenceFileName(scalar.containingFile.virtualFile?.name)) return emptyList()

        val keyPath = RoutingYaml.scalarKeyPath(scalar) ?: return emptyList()
        if (!Permissions.isYamlPermissionKeyPath(keyPath)) return emptyList()

        return Permissions.permissionTokens(scalar)
            .map { PermissionsReference(scalar, it.name, it.range) }
    }

    private fun getHandlerReference(scalar: YAMLScalar): PsiReference? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (!RoutingYaml.isRoutingFileName(scalar.containingFile.virtualFile?.name)) return null
        if (!RoutingYaml.isRouteHandlerKeyPath(keyValue.keyPath)) return null

        val textValue = scalar.textValue.trim()
        val handler = RoutingYaml.routeHandlerFromText(textValue) ?: return null

        return RouteHandlerReference(
            scalar,
            handler,
            RoutingYaml.handlerValueTextRange(scalar, textValue, handler)
        )
    }
}
