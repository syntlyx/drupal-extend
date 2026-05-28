package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.yml.YAMLKeyValueFinder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

object Permissions {
    private val permissionValueDelimiters = setOf('+', ',')

    fun isSupportEnabled(project: Project): Boolean {
        return project.drupalExtendSettings.isEnabled
    }

    fun allNames(project: Project): Collection<String> {
        if (!isSupportEnabled(project)) return emptyList()
        return FileBasedIndex.getInstance()
            .getAllProjectKeys(PERMISSIONS_INDEX_KEY, project)
            .sorted()
    }

    fun exists(project: Project, permissionName: String): Boolean {
        if (!isSupportEnabled(project)) return false
        return FileBasedIndex.getInstance()
            .getValues(PERMISSIONS_INDEX_KEY, permissionName, GlobalSearchScope.allScope(project))
            .isNotEmpty()
    }

    fun title(project: Project, permissionName: String): String? {
        if (!isSupportEnabled(project)) return null

        var title: String? = null
        val psiManager = PsiManager.getInstance(project)
        val processor = FileBasedIndex.ValueProcessor<String> { file, keyPath ->
            val yamlFile = psiManager.findFile(file) as? YAMLFile ?: return@ValueProcessor true
            val keyValue = YAMLKeyValueFinder(keyPath).findIn(yamlFile) ?: return@ValueProcessor true
            if (keyValue.keyText != permissionName) return@ValueProcessor true

            title = ((keyValue.value as? YAMLMapping)
                ?.keyValues
                ?.firstOrNull { it.keyText == "title" }
                ?.value as? YAMLScalar)
                ?.textValue
                ?.trim()

            title == null
        }

        FileBasedIndex.getInstance()
            .processValues(PERMISSIONS_INDEX_KEY, permissionName, null, processor, GlobalSearchScope.allScope(project))

        return title
    }

    fun isYamlPermissionReferenceFileName(fileName: String?): Boolean {
        return RoutingYaml.isRouteReferenceFileName(fileName)
    }

    fun isYamlPermissionKeyPath(keyPath: String): Boolean {
        return keyPath == "permission"
                || keyPath == "permissions"
                || keyPath == "requirements._permission"
                || keyPath.endsWith(".permission")
                || keyPath.endsWith(".permissions")
                || keyPath.endsWith(".requirements._permission")
    }

    fun permissionTokens(scalar: YAMLScalar): List<PermissionToken> {
        val value = scalar.textValue
        if (value.isBlank()) return emptyList()

        val valueStart = scalar.text.indexOf(value).takeIf { it >= 0 } ?: 0
        val tokens = mutableListOf<PermissionToken>()
        var tokenStart = 0

        for (index in 0..value.length) {
            val isDelimiter = index == value.length || value[index] in permissionValueDelimiters
            if (!isDelimiter) continue

            val rawToken = value.substring(tokenStart, index)
            val leadingWhitespace = rawToken.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: rawToken.length
            val trailingWhitespace = rawToken.indexOfLast { !it.isWhitespace() }.takeIf { it >= 0 } ?: -1

            if (leadingWhitespace <= trailingWhitespace) {
                val start = tokenStart + leadingWhitespace
                val end = tokenStart + trailingWhitespace + 1
                tokens.add(
                    PermissionToken(
                        name = value.substring(start, end),
                        range = TextRange(valueStart + start, valueStart + end)
                    )
                )
            }

            tokenStart = index + 1
        }

        return tokens
    }

    fun permissionTokenAt(scalar: YAMLScalar, element: PsiElement): PermissionToken? {
        val relativeOffset = (element.textRange.startOffset - scalar.textRange.startOffset)
            .coerceIn(0, scalar.textLength)

        return permissionTokens(scalar)
            .firstOrNull { it.range.containsOffset(relativeOffset) }
            ?: permissionTokens(scalar).singleOrNull()
    }

    fun currentPermissionPrefix(scalar: YAMLScalar, caretOffset: Int): String {
        val value = scalar.textValue
        if (value.isBlank()) return ""

        val valueStart = scalar.text.indexOf(value).takeIf { it >= 0 } ?: 0
        val offsetInScalar = (caretOffset - scalar.textRange.startOffset).coerceIn(0, scalar.textLength)
        val offsetInValue = (offsetInScalar - valueStart).coerceIn(0, value.length)
        val tokenStart = value
            .substring(0, offsetInValue)
            .indexOfLast { it in permissionValueDelimiters }
            .let { if (it >= 0) it + 1 else 0 }

        return value.substring(tokenStart, offsetInValue).trimStart()
    }

    fun permissionDefinitionFromKeyElement(element: PsiElement): YAMLKeyValue? {
        if (element.containingFile?.virtualFile?.name?.endsWith(".permissions.yml") != true) return null

        val keyValue = element as? YAMLKeyValue
            ?: element.parent as? YAMLKeyValue
            ?: PsiTreeUtil.getParentOfType(element, YAMLKeyValue::class.java, false)
            ?: return null
        val keyElement = keyValue.key ?: return null
        if (element != keyValue
            && element != keyElement
            && !PsiTreeUtil.isAncestor(keyElement, element, false)
            && !(element is YAMLScalar && PsiTreeUtil.isAncestor(element, keyElement, false))
        ) return null

        val parentMapping = keyValue.parent as? YAMLMapping ?: return null
        if (parentMapping.parent !is YAMLDocument) return null
        if (keyValue.keyText.isBlank() || keyValue.keyText == "permission_callbacks") return null

        return keyValue
    }

    data class PermissionToken(
        val name: String,
        val range: TextRange,
    )
}
