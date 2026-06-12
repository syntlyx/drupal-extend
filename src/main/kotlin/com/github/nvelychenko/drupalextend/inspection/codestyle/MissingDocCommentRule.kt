package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.inspections.phpdoc.GeneratePhpDocQuickFix
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList
import com.jetbrains.php.lang.psi.elements.PhpNamedElement

internal object MissingDocCommentRule : DrupalCodeStyleRule {
    override val id: String = "missing_doc_comment"

    override fun isEnabled(settings: DrupalExtendSettings): Boolean {
        return settings.isCodeStyleMissingDocCommentsEnabled
    }

    override fun inspect(element: PsiElement, context: DrupalCodeStyleContext) {
        when (element) {
            is PhpClass -> inspectClass(element, context)
            is Function -> inspectFunction(element, context)
            is PhpClassFieldsList -> inspectClassFields(element, context)
        }
    }

    private fun inspectClass(phpClass: PhpClass, context: DrupalCodeStyleContext) {
        if (phpClass.isAnonymous) return
        if (phpClass.docComment != null) return

        registerMissingDocCommentProblem(phpClass, "Missing class doc comment", context)
    }

    private fun inspectFunction(function: Function, context: DrupalCodeStyleContext) {
        if (function.isClosure) return
        if (function.docComment != null) return

        registerMissingDocCommentProblem(function, "Missing function doc comment", context)
    }

    /**
     * Constants and properties need a docblock too (Drupal.Commenting.VariableComment; the API
     * documentation standard covers constants). One problem per declaration statement — a
     * multi-field `public $a, $b;` shares a single docblock.
     */
    private fun inspectClassFields(fieldsList: PhpClassFieldsList, context: DrupalCodeStyleContext) {
        val firstField = fieldsList.fields.minByOrNull { it.textRange.startOffset } ?: return
        if (firstField.docComment != null) return

        val message = if (firstField.isConstant) {
            "Missing constant doc comment"
        } else {
            "Missing member variable doc comment"
        }
        val fix = DrupalCodeStyleTextEditQuickFix("Add member doc comment") { _, descriptor ->
            val list = descriptor.psiElement as? PhpClassFieldsList
                ?: PsiTreeUtil.getParentOfType(descriptor.psiElement, PhpClassFieldsList::class.java)
                ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
            listOfNotNull(list.missingMemberDocCommentEdit())
        }

        context.register(
            DrupalCodeStyleProblem(
                element = fieldsList,
                message = message,
                highlightType = ProblemHighlightType.WEAK_WARNING,
                fixes = listOf(fix)
            )
        )
    }

    private fun registerMissingDocCommentProblem(
        element: PhpNamedElement,
        message: String,
        context: DrupalCodeStyleContext,
    ) {
        val nameElement = element.nameNode?.psi ?: element
        val fix = if (context.isOnTheFly) GeneratePhpDocQuickFix.ON_THE_FLY else GeneratePhpDocQuickFix.INSTANCE

        context.register(
            DrupalCodeStyleProblem(
                element = nameElement,
                message = message,
                highlightType = ProblemHighlightType.WEAK_WARNING,
                fixes = listOf(fix),
                includeFileFix = false
            )
        )
    }
}

/**
 * Builds the insertion of a Drupal-format docblock above an undocumented constant/property
 * declaration: a summary humanized from the first field's name, plus a `@var` tag with the
 * declared type (fully qualified via the use table) for properties. Returns null when the
 * declaration already has a docblock.
 *
 * Exposed as an extension so the file-wide fixer can apply it too.
 */
internal fun PhpClassFieldsList.missingMemberDocCommentEdit(): TextEdit? {
    val firstField = fields.minByOrNull { it.textRange.startOffset } ?: return null
    if (firstField.docComment != null) return null

    val fileText = containingFile?.text ?: return null
    val listStartOffset = textRange.startOffset
    val lineStartOffset = fileText.lastIndexOf('\n', (listStartOffset - 1).coerceAtLeast(0))
        .takeIf { it >= 0 }?.plus(1) ?: 0
    val indentation = fileText.substring(lineStartOffset, listStartOffset).takeWhile { it == ' ' || it == '\t' }

    val summary = "The ${humanizeIdentifier(firstField.name.removePrefix("\$"))}."
    val docBlock = StringBuilder()
        .append(indentation).append("/**\n")
        .append(indentation).append(" * ").append(summary).append("\n")
    if (!firstField.isConstant) {
        val varType = firstField.typeDeclaration?.let { renderPhpTypeDeclaration(it) } ?: "mixed"
        docBlock.append(indentation).append(" *\n")
        docBlock.append(indentation).append(" * @var ").append(varType).append("\n")
    }
    docBlock.append(indentation).append(" */\n")

    return TextEdit(lineStartOffset, docBlock.toString())
}
