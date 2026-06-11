package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.findVariablesByName
import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.DrupalTheme
import com.github.nvelychenko.drupalextend.patterns.Patterns.SIMPLE_FUNCTION
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.yml.FunctionFinderInContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.PhpPsiUtil
import com.jetbrains.php.lang.psi.elements.*
import com.jetbrains.php.lang.psi.elements.Function
import kotlinx.serialization.serializer

class ThemeIndex : FileBasedIndexExtension<String, DrupalTheme>() {

    override fun getIndexer(): DataIndexer<String, DrupalTheme, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, DrupalTheme>()

            if (!inputData.project.drupalExtendSettings.isEnabled) return@DataIndexer map
            if (!inputData.isValidForIndex()) return@DataIndexer map
            handleLegacyHooks(map, inputData)
            handleHooks(map, inputData)

            return@DataIndexer map
        }
    }

    private fun handleHooks(map: java.util.HashMap<String, DrupalTheme>, inputData: FileContent) {
        val phpFile = inputData.psiFile as PhpFile
        val clazz = PsiTreeUtil.findChildOfType(phpFile, PhpClass::class.java) ?: return

        if (clazz.fqn == "\\Drupal\\Core\\Theme\\ThemeCommonElements") {
            val method = clazz.findMethodByName("commonElements") ?: return
            collectThemesFromFunction(method, map, "commonElements")
        }

        if (!clazz.namespaceName.startsWith("\\Drupal\\") || !clazz.namespaceName.endsWith("\\Hook\\")) return

        val attributes = PsiTreeUtil.findChildrenOfType(clazz, PhpAttribute::class.java)

        for (attribute in attributes) {
            if (attribute.fqn != "\\Drupal\\Core\\Hook\\Attribute\\Hook") continue
            // A bare `#[Hook]` (no arguments yet) is normal mid-typing PHP.
            val hookNameArgument = attribute.parameters.firstOrNull() ?: continue

            if (hookNameArgument !is StringLiteralExpression || hookNameArgument.contents != "theme") continue

            val holder = attribute.parent.parent as? Method ?: continue
            collectThemesFromFunction(holder, map, holder.name)
        }

    }

    private fun handleLegacyHooks(map: HashMap<String, DrupalTheme>, inputData: FileContent) {
        val fileName = inputData.fileName
        val moduleName = fileName.substringBefore(".")
        if (!fileName.endsWith(".module") && fileName != "theme.inc") {
            return
        }

        val finder = FunctionFinderInContext(arrayOf(moduleName + "_theme", "drupal_common_theme"), SIMPLE_FUNCTION)
        val themeHook = finder.findIn(inputData.psiFile) ?: return
        collectThemesFromFunction(themeHook, map, themeHook.name)
    }

    private fun collectThemesFromFunction(themeHook: Function, map: HashMap<String, DrupalTheme>, hookName: String) {
        when (val returnValue = PsiTreeUtil.findChildOfType(themeHook, PhpReturn::class.java)?.firstPsiChild) {
            is ArrayCreationExpression -> processArrayCreationExpression(returnValue, map, hookName)
            is Variable -> processVariable(returnValue, themeHook, map, hookName)
            is FunctionReference -> {
                if (returnValue.name == "array_merge") {
                    returnValue.parameters.forEach {
                        if (it is ArrayCreationExpression) {
                            processArrayCreationExpression(it, map, hookName)
                        }
                    }
                }
            }
        }
    }

    private fun processVariable(
        returnVariable: Variable,
        themeHook: Function,
        map: HashMap<String, DrupalTheme>,
        hookName: String,
    ) {
        for (variable in themeHook.findVariablesByName(returnVariable.name)) {
            val assignment = PhpPsiUtil.getParentOfClass(variable, AssignmentExpression::class.java) ?: continue

            val propertiesHash = assignment.value as? ArrayCreationExpression ?: continue

            // $theme['foo']
            val themeName =
                ((assignment.variable as? ArrayAccessExpression)?.index?.value as? StringLiteralExpression)?.contents ?: continue

            map[themeName] = DrupalTheme(themeName, getThemeVariables(propertiesHash), hookName)
        }
    }

    private fun processArrayCreationExpression(
        returnType: ArrayCreationExpression,
        map: HashMap<String, DrupalTheme>,
        hookName: String,
    ) {
        top@ for (topHash in returnType.hashElements) {
            val propertiesHash = topHash.value
            val themeNamePsi = topHash.key
            if (themeNamePsi !is StringLiteralExpression || themeNamePsi.contents.isEmpty() || propertiesHash !is ArrayCreationExpression) continue

            map[themeNamePsi.contents] = DrupalTheme(themeNamePsi.contents, getThemeVariables(propertiesHash), hookName)

        }
    }

    private fun getThemeVariables(propertiesHash: ArrayCreationExpression): Array<String> {
        val variables = mutableListOf<String>()
        for (it in propertiesHash.hashElements) {
            val variablesHash = it.value
            if ((it.key as? StringLiteralExpression)?.contents != "variables" || variablesHash !is ArrayCreationExpression) continue

            variablesHash.hashElements.forEach {
                val themeName = it.key
                if (themeName is StringLiteralExpression && themeName.contents.isNotEmpty()) {
                    variables.add(themeName.contents)
                }
            }

            return variables.toTypedArray()
        }

        return emptyArray()
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor()

    private val myDataExternalizer: DataExternalizer<DrupalTheme> =
        SerializedObjectDataExternalizer(serializer<DrupalTheme>())

    override fun getName(): ID<String, DrupalTheme> {
        return THEME_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<DrupalTheme> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file -> file.fileType == PhpFileType.INSTANCE }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 2

}
