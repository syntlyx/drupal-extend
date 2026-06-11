package com.github.nvelychenko.drupalextend.inspection

import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

class DrupalInfoYamlInspection : LocalInspectionTool() {
    private val emptyVisitor = object : PsiElementVisitor() {}

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project

        if (!isSupportEnabled(project)) return emptyVisitor
        if (DumbService.getInstance(project).isDumb) return emptyVisitor

        val virtualFile = holder.file.virtualFile ?: return emptyVisitor
        if (!isInfoFileName(virtualFile.name)) return emptyVisitor
        if (isUnderTestOrFixturePath(project, virtualFile)) return emptyVisitor

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is YAMLFile) return
                inspectInfoFile(element, holder)
            }
        }
    }

    private fun inspectInfoFile(file: YAMLFile, holder: ProblemsHolder) {
        val mapping = file.documents
            .asSequence()
            .mapNotNull { it.topLevelValue as? YAMLMapping }
            .firstOrNull()

        // Fail soft on malformed/empty YAML: report missing keys against the file element.
        val anchor: PsiElement = mapping ?: file

        val nameValue = mapping?.getKeyValueByKey("name")
        if (nameValue == null) {
            holder.registerProblem(
                anchor,
                "Missing required key 'name' in info.yml",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }

        val typeValue = mapping?.getKeyValueByKey("type")
        if (typeValue == null) {
            holder.registerProblem(
                anchor,
                "Missing required key 'type' in info.yml",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        } else {
            inspectType(typeValue, holder)
        }

        inspectCoreVersionRequirement(mapping, anchor, holder)
        inspectDependencies(mapping, holder)
    }

    private fun inspectType(typeValue: YAMLKeyValue, holder: ProblemsHolder) {
        val scalar = typeValue.value as? YAMLScalar ?: return
        val type = scalar.textValue.trim()
        if (type.isEmpty() || type in VALID_TYPES) return

        holder.registerProblem(
            scalar,
            "Invalid extension type '$type', expected one of: ${VALID_TYPES.joinToString(", ")}",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
    }

    private fun inspectCoreVersionRequirement(
        mapping: YAMLMapping?,
        anchor: PsiElement,
        holder: ProblemsHolder
    ) {
        mapping ?: return
        if (mapping.getKeyValueByKey("core_version_requirement") != null) return
        // A legacy `core` key satisfies the requirement on older cores.
        if (mapping.getKeyValueByKey("core") != null) return

        holder.registerProblem(
            anchor,
            "Missing 'core_version_requirement'",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
    }

    private fun inspectDependencies(mapping: YAMLMapping?, holder: ProblemsHolder) {
        val dependencies = mapping?.getKeyValueByKey("dependencies")?.value as? YAMLSequence ?: return

        for (item in dependencies.items) {
            val scalar = item.value as? YAMLScalar ?: continue
            val entry = scalar.textValue.trim()
            if (entry.isEmpty() || DEPENDENCY_PATTERN.matches(entry)) continue

            holder.registerProblem(
                scalar,
                "Invalid dependency '$entry', expected 'module' or 'project:module'",
                ProblemHighlightType.WEAK_WARNING
            )
        }
    }

    private fun isSupportEnabled(project: Project): Boolean {
        return project.drupalExtendSettings.isEnabled
    }

    private fun isInfoFileName(fileName: String?): Boolean {
        if (fileName == null || !fileName.endsWith(INFO_YML_SUFFIX)) return false
        val machineName = fileName.removeSuffix(INFO_YML_SUFFIX)
        return machineName.isNotEmpty() && MACHINE_NAME_PATTERN.matches(machineName)
    }

    private fun isUnderTestOrFixturePath(project: Project, file: VirtualFile): Boolean {
        val projectDir = project.guessProjectDir() ?: return false
        val relativePath = VfsUtil.getRelativePath(file, projectDir, '/') ?: return false
        return TEST_FIXTURE_PATH_SEGMENTS.any { relativePath.contains(it) }
    }

    private companion object {
        private const val INFO_YML_SUFFIX = ".info.yml"

        private val VALID_TYPES = setOf("module", "theme", "profile", "theme_engine")

        private val MACHINE_NAME_PATTERN = Regex("[a-z0-9_]+")

        private val DEPENDENCY_PATTERN = Regex("[a-z0-9_]+(:[a-z0-9_]+)?")

        private val TEST_FIXTURE_PATH_SEGMENTS = listOf(
            "/Test/", "/Tests/", "/tests/",
            "/Fixture/", "/Fixtures/", "/fixture/", "/fixtures/"
        )
    }
}
