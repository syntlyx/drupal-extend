package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.util.DrupalConfigUsagePlugins
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Where config-enabled plugins are *defined*: `kind|id` (see [PluginConfigUsageIndex.usageKey])
 * → class FQN, for the plugin types [DrupalConfigUsagePlugins] recognizes. Lets a plugin id in
 * config YAML resolve back to its class — the navigation dual of [PluginConfigUsageIndex].
 */
class PluginDefinitionIndex : FileBasedIndexExtension<String, String>() {
    override fun getIndexer(): DataIndexer<String, String, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, String>()

            if (!inputData.isValidForIndex()) return@DataIndexer map
            val phpFile = inputData.psiFile as? PhpFile ?: return@DataIndexer map

            for (phpClass in PsiTreeUtil.findChildrenOfType(phpFile, PhpClass::class.java)) {
                val (kind, id) = DrupalConfigUsagePlugins.pluginKindAndId(phpClass) ?: continue
                map[PluginConfigUsageIndex.usageKey(kind, id)] = phpClass.fqn
            }

            map
        }
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getName(): ID<String, String> {
        return PLUGIN_DEFINITION_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<String> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file -> file.fileType == PhpFileType.INSTANCE }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1
}
