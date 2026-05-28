package com.github.nvelychenko.drupalextend.reference.referenceType

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.Permissions
import com.github.nvelychenko.drupalextend.util.yml.YAMLKeyValueFinder
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.PhpFileType
import org.jetbrains.yaml.YAMLFileType

class PermissionsReference(
    element: PsiElement,
    private val permissionName: String,
    rangeInElement: TextRange = TextRange(0, element.textLength),
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement) {

    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }
    private val project = element.project
    private val scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
        GlobalSearchScope.allScope(project),
        XmlFileType.INSTANCE,
        YAMLFileType.YML,
        PhpFileType.INSTANCE
    )

    override fun getCanonicalText(): String {
        return permissionName
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (Permissions.permissionDefinitionFromKeyElement(element)?.keyText == permissionName) return true
        return super.isReferenceTo(element)
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (!project.drupalExtendSettings.isEnabled) return emptyArray()
        val files = HashMap<String, VirtualFile>()
        val processor = FileBasedIndex.ValueProcessor<String> { file, value -> files[value] = file; true }

        FileBasedIndex
            .getInstance()
            .processValues(PERMISSIONS_INDEX_KEY, permissionName, null, processor, scope)

        val navigationItems = mutableListOf<PsiElement>()
        files.forEach {
            val path = it.key
            val virtualFile = it.value
            val yamlFile = psiManager.findFile(virtualFile)
            val keyValue = YAMLKeyValueFinder(path).findIn(yamlFile as PsiElement) ?: return@forEach
            navigationItems.add(keyValue.key ?: keyValue.value ?: keyValue)

        }
        return PsiElementResolveResult.createResults(navigationItems)
    }

}
