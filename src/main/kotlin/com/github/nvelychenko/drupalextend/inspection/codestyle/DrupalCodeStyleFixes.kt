package com.github.nvelychenko.drupalextend.inspection.codestyle

import com.github.nvelychenko.drupalextend.php.edit.TextEdit
import com.github.nvelychenko.drupalextend.project.DrupalExtendSettings
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass

internal object DrupalCodeStyleFixes {
    val fixFile = DrupalCodeStyleTextEditQuickFix("Fix Drupal code style in file") { project, descriptor ->
        val file = descriptor.psiElement.containingFile ?: return@DrupalCodeStyleTextEditQuickFix emptyList()
        file.fullFileFixEdits(project.drupalExtendSettings)
    }
}

private fun PsiFile.fullFileFixEdits(settings: DrupalExtendSettings): Collection<TextEdit> {
    val document = document() ?: return emptyList()
    val edits = mutableListOf<TextEdit>()

    PsiTreeUtil.findChildrenOfType(this, PhpDocComment::class.java)
        .mapNotNullTo(edits) { it.fullDocBlockFixEdit(settings) }

    if (settings.isCodeStyleConstantCasingEnabled) {
        edits += constantCasingEdits()
    }

    if (settings.isCodeStyleFunctionSpacingEnabled) {
        PsiTreeUtil.findChildrenOfType(this, Function::class.java)
            .filterNot { it.isClosure }
            .flatMapTo(edits) { it.spacingFixEdits(document) }
    }

    if (settings.isCodeStyleClassCloseBraceSpacingEnabled) {
        PsiTreeUtil.findChildrenOfType(this, PhpClass::class.java)
            .filterNot { it.isAnonymous }
            .mapNotNullTo(edits) { it.classCloseBraceSpacingFixEdit(document) }
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
            .flatMapTo(edits) { it.commaSpacingEdits() }
        PsiTreeUtil.findChildrenOfType(this, ArrayCreationExpression::class.java)
            .flatMapTo(edits) { it.commaSpacingEdits() }
    }

    if (settings.isCodeStyleUseStatementSortingEnabled) {
        edits += sortUseStatementEdits()
    }

    if (settings.isCodeStyleClosingPhpTagEnabled) {
        closingPhpTagEdit()?.let { edits += it }
    }

    return edits.distinctBy { Triple(it.startOffset, it.endOffset, it.text) }
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
