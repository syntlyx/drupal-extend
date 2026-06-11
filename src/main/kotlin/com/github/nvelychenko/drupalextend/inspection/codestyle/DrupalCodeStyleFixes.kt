package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.php.edit.applyTo
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClassFieldsList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpSwitch

internal object DrupalCodeStyleFixes {
    val fixFile = object : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Fix Drupal code style in file"
        }

        override fun getName(): String {
            return familyName
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement.containingFile ?: return
            DrupalCodeStyleFileFixer.apply(project, file)
        }
    }
}

internal object DrupalCodeStyleFileFixer {
    fun reformatAndApply(project: Project, file: PsiFile): Boolean {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return false
        val beforeText = document.text
        val documentManager = PsiDocumentManager.getInstance(project)

        documentManager.commitDocument(document)
        CodeStyleManager.getInstance(project).reformatText(file, 0, document.textLength)
        documentManager.commitDocument(document)

        apply(project, file)

        return beforeText != document.text
    }

    fun apply(project: Project, file: PsiFile): Boolean {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return false
        val beforeText = document.text
        val settings = project.drupalExtendSettings

        if (settings.isCodeStyleShortArraySyntaxEnabled) {
            PhpStormShortArraySyntaxFixes.applyToFile(file)
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        }

        val edits = file.drupalCodeStyleFileEdits(project.drupalExtendSettings)
        if (edits.isEmpty()) return beforeText != document.text

        edits.applyTo(document)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return beforeText != document.text
    }
}

internal fun PsiFile.drupalCodeStyleFileEdits(settings: DrupalExtendSettings): Collection<TextEdit> {
    val document = document() ?: return emptyList()
    val edits = mutableListOf<TextEdit>()

    if (settings.isCodeStyleFileDocCommentEnabled && this is PhpFile) {
        fileDocCommentEdit()?.let { edits += it }
    }

    PsiTreeUtil.findChildrenOfType(this, PhpDocComment::class.java)
        .mapNotNullTo(edits) { it.fullDocBlockFixEdit(settings) }

    if (settings.isCodeStyleDocBlockBlankLineNormalizationEnabled) {
        PsiTreeUtil.findChildrenOfType(this, PhpDocComment::class.java)
            .mapNotNullTo(edits) { it.declarationSpacingEdit(document) }
    }

    if (settings.isCodeStyleDocBlockSummaryLengthEnabled) {
        PsiTreeUtil.findChildrenOfType(this, PhpDocComment::class.java)
            .mapNotNull { it.summaryWrapEdit() }
            .filterNot { wrapEdit -> edits.any { it.overlaps(wrapEdit) } }
            .toCollection(edits)
    }

    if (settings.isCodeStyleFunctionDocMissingTagsEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Function::class.java)
            .filterNot { it.isClosure }
            .flatMap { it.missingDocTagsEdits() }
            .filterNot { tagEdit -> edits.any { it.overlaps(tagEdit) } }
            .toCollection(edits)
    }

    if (settings.isCodeStyleConstantCasingEnabled) {
        edits += constantCasingEdits()
    }

    if (settings.isCodeStyleMemberVisibilityEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Method::class.java)
            .mapNotNullTo(edits) { it.memberVisibilityEdit() }
        PsiTreeUtil.findChildrenOfType(this, PhpClassFieldsList::class.java)
            .mapNotNullTo(edits) { it.memberVisibilityEdit() }
        PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .flatMapTo(edits) { it.classConstantVisibilityEdits() }
    }

    if (settings.isCodeStyleFunctionSpacingEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Function::class.java)
            .filterNot { it.isClosure }
            .flatMapTo(edits) { it.spacingFixEdits(document) }
    }

    if (settings.isCodeStyleFunctionDeclarationStyleEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Function::class.java)
            .filterNot { it.isClosure }
            .flatMapTo(edits) { it.functionDeclarationStyleEdits(document) }
    }

    if (settings.isCodeStyleClassCloseBraceSpacingEnabled) {
        PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .filterNot { it.isAnonymous }
            .mapNotNullTo(edits) { it.classCloseBraceSpacingFixEdit(document) }
    }

    if (settings.isCodeStyleBlockBlankLinesEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Function::class.java)
            .flatMapTo(edits) { it.blockBlankLineEdits(document) }
        PsiTreeUtil.collectElements(this) { element ->
            element is ControlStatement || element is PhpSwitch || element is Else
        }
            .flatMapTo(edits) { it.blockBlankLineEdits(document) }
    }

    if (settings.isCodeStyleCastSpacingEnabled) {
        PsiTreeUtil.collectElements(this) { element ->
            element.node?.elementType == PhpElementTypes.CAST_EXPRESSION
        }
            .mapNotNullTo(edits) { it.castSpacingEdit() }
    }

    if (settings.isCodeStyleOperatorSpacingEnabled) {
        PsiTreeUtil.findChildrenOfType(this, AssignmentExpression::class.java)
            .mapNotNullTo(edits) { it.assignmentOperatorSpacingEdit() }
        PsiTreeUtil.findChildrenOfType(this, ArrayHashElement::class.java)
            .mapNotNullTo(edits) { it.arrayArrowSpacingEdit() }
        PsiTreeUtil.findChildrenOfType(this, BinaryExpression::class.java)
            .mapNotNullTo(edits) { it.binaryOperatorSpacingEdit() }
    }

    if (settings.isCodeStyleCommaSpacingEnabled) {
        PsiTreeUtil.findChildrenOfType(this, ParameterList::class.java)
            .flatMap { it.commaSpacingEdits() }
            .filterNot { commaEdit -> edits.any { it.overlaps(commaEdit) } }
            .toCollection(edits)
        PsiTreeUtil.findChildrenOfType(this, ArrayCreationExpression::class.java)
            .flatMapTo(edits) { it.commaSpacingEdits() }
    }

    if (settings.isCodeStyleMultilineArrayStyleEnabled) {
        PsiTreeUtil.findChildrenOfType(this, ArrayCreationExpression::class.java)
            .flatMapTo(edits) { it.multilineArrayStyleEdits(document) }
    }

    if (settings.isCodeStyleControlStructureSpacingEnabled) {
        PsiTreeUtil.collectElements(this) { element ->
            element is ControlStatement || element is PhpSwitch || element is Else
        }
            .flatMapTo(edits) { it.controlStructureSpacingEdits() }
    }

    if (settings.isCodeStyleElseIfSyntaxEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Else::class.java)
            .mapNotNullTo(edits) { it.elseIfSyntaxEdit() }
    }

    if (settings.isCodeStyleFullyQualifiedClassNameEnabled) {
        edits += fullyQualifiedClassNameUseEdits()
    }

    if (settings.isCodeStyleUseStatementSortingEnabled) {
        edits += sortUseStatementEdits()
            .filterNot { sortEdit -> edits.any { it.overlaps(sortEdit) } }
    }

    val closingPhpTagEdit = if (settings.isCodeStyleClosingPhpTagEnabled) closingPhpTagEdit() else null
    closingPhpTagEdit?.let { edits += it }

    if (settings.isCodeStyleFileWhitespaceEnabled && closingPhpTagEdit == null) {
        edits += fileWhitespaceEdits(document)
    }

    return edits.distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
}

private fun TextEdit.overlaps(other: TextEdit): Boolean {
    return startOffset < other.endOffset && other.startOffset < endOffset
}

private fun Function.spacingFixEdits(document: Document): List<TextEdit> {
    val edits = mutableListOf<TextEdit>()
    val anchor = docComment ?: this
    val startLine = document.lineNumber(anchor)
    if (document.previousNonBlankLine(startLine)?.trim() != "<?php" && document.blankLinesBefore(startLine) != 1) {
        document.normalizeBlankLinesBeforeEdit(startLine)?.let { edits += it }
    }

    val endLine = document.endLineNumber(this)
    if (document.nextNonBlankLine(endLine) != null && document.blankLinesAfter(endLine) != 1) {
        document.normalizeBlankLinesAfterEdit(endLine)?.let { edits += it }
    }

    return edits
}

private fun PhpClass.classCloseBraceSpacingFixEdit(document: Document): TextEdit? {
    val classStartLine = document.lineNumber(this)
    val closeBraceLine = document.endLineNumber(this)
    if (closeBraceLine <= classStartLine) return null
    if (!document.lineText(closeBraceLine).trimStart().startsWith("}")) return null
    if (document.blankLinesBefore(closeBraceLine) > 0) return null

    return document.normalizeBlankLinesBeforeEdit(closeBraceLine)
}
