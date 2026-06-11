package com.github.nvelychenko.drupalextend.index

import com.github.nvelychenko.drupalextend.extensions.findVariablesByName
import com.github.nvelychenko.drupalextend.extensions.isValidForIndex
import com.github.nvelychenko.drupalextend.index.dataExternalizer.SerializedObjectDataExternalizer
import com.github.nvelychenko.drupalextend.index.types.RenderElementType
import com.github.nvelychenko.drupalextend.index.types.RenderElementType.RenderElementTypeProperty
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.PhpPsiUtil
import com.jetbrains.php.lang.psi.elements.*
import kotlinx.serialization.serializer

class RenderElementIndex : FileBasedIndexExtension<String, RenderElementType>() {

    override fun getIndexer(): DataIndexer<String, RenderElementType, FileContent> {
        return DataIndexer { inputData ->
            val map = hashMapOf<String, RenderElementType>()

            if (!inputData.project.drupalExtendSettings.isEnabled) return@DataIndexer map

            val psiFile = inputData.psiFile

            if (!inputData.isValidForIndex()) {
                return@DataIndexer map
            }

            val phpClass = PsiTreeUtil.findChildOfType(psiFile, PhpClass::class.java) ?: return@DataIndexer map

            val specialClasses = arrayOf(
                "\\Drupal\\Core\\Render\\Element\\FormElement",
                "\\Drupal\\Core\\Render\\Element\\FormElementBase",
                "\\Drupal\\Core\\Render\\Element\\RenderElement",
                "\\Drupal\\Core\\Render\\Element\\RenderElementBase",
            )

            // A docblock is optional: attribute-only render elements are valid modern Drupal.
            val docCommentText = phpClass.docComment?.text.orEmpty()

            if (specialClasses.contains(phpClass.fqn) && docCommentText.contains(" * - #")) {
                var properties = getAdditionalParametersFromDoc(docCommentText)

                val isFormElement = phpClass.fqn == "\\Drupal\\Core\\Render\\Element\\FormElement"
                if (isFormElement) {
                    properties += arrayOf(RenderElementTypeProperty("name", type = "string"))
                }

                val renderElementType = if (isFormElement) {
                    "FormElement"
                } else {
                    "RenderElement"
                }
                map[phpClass.fqn] = RenderElementType(phpClass.fqn, phpClass.fqn, properties, renderElementType)
            } else {
                processRenderElement(phpClass, map, docCommentText)
            }

            map
        }
    }

    private fun processRenderElement(
        phpClass: PhpClass,
        map: HashMap<String, RenderElementType>,
        docCommentString: String
    ) {
        var type = "RenderElement"

        var renderElementIdId = phpClass.attributes
            .find {
                val fqn = it.fqn
                val isForm = fqn == "\\Drupal\\Core\\Render\\Attribute\\FormElement"
                val isRender = fqn == "\\Drupal\\Core\\Render\\Attribute\\RenderElement"

                if (isForm) {
                    type = "FormElement"
                }

                isForm || isRender
            }
            // firstOrNull: a bare attribute with no arguments is normal mid-typing PHP.
            ?.arguments?.firstOrNull()?.argument?.value?.let { StringUtil.unquoteString(it) }

        if (renderElementIdId == null) {
            // Legacy annotation fallback needs a docblock; attribute-only classes don't.
            val docComment = phpClass.docComment ?: return
            var renderElement = docComment.getTagElementsByName("@RenderElement").firstOrNull()

            if (renderElement == null) {
                renderElement = docComment.getTagElementsByName("@FormElement").firstOrNull() ?: return
                type = "FormElement"
            }

            renderElementIdId = renderElement.text.substringAfter('"').substringBefore('"')
        }

        val getInfoMethod = phpClass.findOwnMethodByName("getInfo") ?: return

        var parameters = getInfoMethod
            .let { PsiTreeUtil.findChildOfType(it, PhpReturn::class.java)?.firstPsiChild }
            .takeIf { it is Variable || it is ArrayCreationExpression || (it as? BinaryExpression)?.operationType == PhpTokenTypes.opPLUS }
            ?.let {
                when (it) {
                    is Variable -> processVariables(getInfoMethod, it)
                    is ArrayCreationExpression -> processArrayCreationExpression(it)
                    is BinaryExpression -> {
                        var value = mutableListOf<RenderElementTypeProperty>()
                        if (it.leftOperand is ArrayCreationExpression) {
                            value =
                                processArrayCreationExpression(it.leftOperand as ArrayCreationExpression).toMutableList()
                        }

                        if (it.rightOperand is ArrayCreationExpression) {
                            value.addAll(processArrayCreationExpression(it.rightOperand as ArrayCreationExpression).toMutableList())
                        }
                        value.toTypedArray()
                    }

                    else -> null
                }
            } ?: return

        if (docCommentString.contains(" * - #")) {
            parameters = getAdditionalParametersFromDoc(docCommentString, 10.0) + parameters
        }

        map[renderElementIdId] = RenderElementType(renderElementIdId, phpClass.fqn, parameters, type)
    }

    private fun processArrayCreationExpression(expression: ArrayCreationExpression): Array<RenderElementTypeProperty> {
        val parameters = ArrayList<RenderElementTypeProperty>(5)

        expression.hashElements.forEach { hashElement ->
            val key = hashElement.key
            if (key is StringLiteralExpression) {
                parameters.add(RenderElementTypeProperty(key.contents, 10.0, null, null))
            }

        }

        return parameters.toTypedArray()
    }

    private fun processVariables(method: Method, returnType: Variable): Array<RenderElementTypeProperty> {
        val parameters = ArrayList<RenderElementTypeProperty>(5)
        method.findVariablesByName(returnType.name).forEach { variable ->
            val assignment = PhpPsiUtil.getParentOfClass(variable, AssignmentExpression::class.java) ?: return@forEach

            val index = (assignment.variable as? ArrayAccessExpression)?.index ?: return@forEach
            val indexValue = index.value

            if (indexValue is StringLiteralExpression) {
                parameters.add(RenderElementTypeProperty(indexValue.contents, 10.0, null, null))
            }
        }

        return parameters.toTypedArray()
    }

    private fun getAdditionalParametersFromDoc(
        docCommentText: String,
        defaultPriority: Double = 0.0
    ): Array<RenderElementTypeProperty> {
        val doc = docCommentText.replace(docCommentText.substringBefore(" * - #"), "")


        val parameters = ArrayList<RenderElementTypeProperty>(8)
        // @todo Write wtf is going on here
        doc.split(" * - #").forEach { rowDocument ->
            if (rowDocument.trim().isEmpty()) return@forEach
            // @todo Implement documentation.
//            val document = rowDocument.replace("*", "").replace("\\s{2,}".toRegex(), " ")
            val id = rowDocument.substringBefore(":")
            val hasType = rowDocument.startsWith("${id}: (")

            val type = rowDocument.takeIf { hasType }?.substringAfter("(")?.substringBefore(")")
            //
            //            val documentation = if (hasDocument) {
            //                document.substringAfter("${type})")
            //            } else {
            //                document.substringAfter("${id}: ").trim()
            //            }

            parameters.add(RenderElementTypeProperty(id, defaultPriority, type, null))
        }
        return parameters.toTypedArray()
    }

    private val myKeyDescriptor: KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    private val myDataExternalizer: DataExternalizer<RenderElementType> =
        SerializedObjectDataExternalizer(serializer<RenderElementType>())

    override fun getName(): ID<String, RenderElementType> {
        return RENDER_ELEMENT_INDEX_KEY
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = myKeyDescriptor

    override fun getValueExternalizer(): DataExternalizer<RenderElementType> = myDataExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file -> file.fileType == PhpFileType.INSTANCE }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 3

}
