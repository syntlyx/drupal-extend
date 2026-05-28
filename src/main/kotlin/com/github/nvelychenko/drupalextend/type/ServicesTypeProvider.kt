package com.github.nvelychenko.drupalextend.type

import com.github.nvelychenko.drupalextend.patterns.Patterns.METHOD_WITH_FIRST_STRING_PARAMETER
import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.decodeServiceIdFromTypeSignature
import com.github.nvelychenko.drupalextend.util.encodeServiceIdForTypeSignature
import com.github.nvelychenko.drupalextend.util.getServiceMethodReference
import com.github.nvelychenko.drupalextend.util.isContainerGetCall
import com.github.nvelychenko.drupalextend.util.isDrupalServiceCall
import com.github.nvelychenko.drupalextend.util.normalizeServiceClassFqn
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ServicesTypeProvider : PhpTypeProvider4 {
    override fun getKey(): Char {
        return KEY
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        val project = psiElement.project
        if (
            !Services.isSupportEnabled(project)
            || DumbService.getInstance(project).isDumb
            || !METHOD_WITH_FIRST_STRING_PARAMETER.accepts(psiElement)
            || psiElement !is MethodReference
        ) {
            return null
        }

        val firstParameter = psiElement.parameterList?.getParameter(0) as? StringLiteralExpression ?: return null
        if (firstParameter.contents.isBlank()) return null
        if (firstParameter.getServiceMethodReference() != psiElement) return null
        if (!psiElement.isDrupalServiceCall() && !psiElement.isContainerGetCall()) return null

        return PhpType().add("#$key${firstParameter.contents.encodeServiceIdForTypeSignature()}")
    }

    override fun complete(expression: String, project: Project): PhpType {
        val serviceId = expression.substring(2).decodeServiceIdFromTypeSignature()
            ?: return PhpType()
        val serviceClass = Services.className(project, serviceId)
            ?.normalizeServiceClassFqn()
            ?: return PhpType()

        if (serviceClass.isBlank()) return PhpType()

        return PhpType().add(serviceClass)
    }

    override fun getBySignature(
        expression: String,
        visited: Set<String?>?,
        depth: Int,
        project: Project?
    ): Collection<PhpNamedElement> {
        return emptyList()
    }

    companion object {
        const val KEY = '\u3345'
    }
}
