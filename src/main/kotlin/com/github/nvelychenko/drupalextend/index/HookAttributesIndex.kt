package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class HookAttributesIndex : FileBasedIndexExtension<String, String>() {
    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()
            if (!inputData.isValidForIndex()) return@DataIndexer map

            val phpFile = inputData.psiFile as PhpFile

            // Every class in the file: nothing stops a module from declaring several hook
            // classes per file.
            for (clazz in PsiTreeUtil.findChildrenOfType(phpFile, PhpClass::class.java)) {
                if (!clazz.namespaceName.startsWith("\\Drupal\\") || !clazz.namespaceName.endsWith("\\Hook\\")) continue

                val attributes = PsiTreeUtil.findChildrenOfType(clazz, PhpAttribute::class.java)

                for (attribute in attributes) {
                    if (attribute.fqn != "\\Drupal\\Core\\Hook\\Attribute\\Hook") continue
                    // A bare `#[Hook]` (no arguments yet) is normal mid-typing PHP.
                    val hookName = attribute.parameters.firstOrNull() ?: continue

                    if (hookName is StringLiteralExpression && hookName.contents.isNotEmpty()) {
                        map[hookName.contents] = clazz.fqn
                    }
                }
            }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    private val myDataExternalizer = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return HOOK_ATTRIBUTES_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file: VirtualFile -> file.fileType == PhpFileType.INSTANCE }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 3

}

fun getHookAttributesByName(psiElement: PsiElement, name: String): Array<PhpAttribute> {
    val result = mutableListOf<PhpAttribute>()
    val attributes = PsiTreeUtil.findChildrenOfType(psiElement, PhpAttribute::class.java)

    for (attribute in attributes) {
        if (attribute.fqn != "\\Drupal\\Core\\Hook\\Attribute\\Hook") continue
        // A bare `#[Hook]` (no arguments yet) is normal mid-typing PHP.
        val hookName = attribute.parameters.firstOrNull() ?: continue
        if (hookName is StringLiteralExpression && hookName.contents == name) {
            result.add(attribute)
        }
    }

    return result.toTypedArray()
}
