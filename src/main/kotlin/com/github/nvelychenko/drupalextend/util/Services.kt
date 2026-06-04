package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.yml.YAMLKeyValueFinder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

object Services {
    val builtInIds = setOf(
        "service_container",
        "kernel"
    )

    fun isSupportEnabled(project: Project): Boolean {
        val settings = project.drupalExtendSettings

        return settings.isEnabled
                && settings.isServicesCompletionEnabled
    }

    fun allIds(project: Project): List<String> {
        return (FileBasedIndex.getInstance().getAllProjectKeys(SERVICES_INDEX_KEY, project) + builtInIds)
            .distinct()
            .sorted()
    }

    fun className(project: Project, serviceId: String): String? {
        return FileBasedIndex.getInstance().getValue(SERVICES_INDEX_KEY, serviceId, project)
    }

    fun exists(project: Project, serviceId: String, localServiceIds: Collection<String> = emptyList()): Boolean {
        if (builtInIds.contains(serviceId)) return true
        if (localServiceIds.contains(serviceId)) return true

        return FileBasedIndex.getInstance()
            .getValues(SERVICES_INDEX_KEY, serviceId, GlobalSearchScope.allScope(project))
            .isNotEmpty()
    }

    fun referencesClass(project: Project, classFqn: String): Boolean {
        if (!isSupportEnabled(project)) return false

        val expectedClass = classFqn.normalizeServiceClassFqn()
        val index = FileBasedIndex.getInstance()

        return index.getAllProjectKeys(SERVICES_INDEX_KEY, project)
            .any { serviceId ->
                serviceId.normalizeServiceClassFqn() == expectedClass ||
                        index.getValues(SERVICES_INDEX_KEY, serviceId, GlobalSearchScope.allScope(project))
                            .any { it.normalizeServiceClassFqn() == expectedClass }
            }
    }

    fun referencesMethod(project: Project, classFqn: String, methodName: String): Boolean {
        if (!isSupportEnabled(project)) return false

        val expectedClass = classFqn.normalizeServiceClassFqn()

        return serviceFiles(project)
            .flatMap { ServicesYaml.serviceFactoryMethods(it) }
            .any { factoryMethod ->
                factoryMethod.methodName == methodName &&
                        factoryOwnerClassName(project, factoryMethod)?.normalizeServiceClassFqn() == expectedClass
            }
    }

    fun factoryOwnerClassName(project: Project, factoryMethod: ServicesYaml.ServiceFactoryMethod): String? {
        factoryMethod.ownerClassFqn?.let { return it }

        val ownerServiceId = factoryMethod.ownerServiceId ?: return null
        return className(project, ownerServiceId)
            ?.takeIf { it.isNotBlank() }
            ?: ownerServiceId.takeIf { it.contains("\\") }
    }

    fun definition(project: Project, serviceId: String): YAMLKeyValue? {
        var serviceDefinition: YAMLKeyValue? = null
        val psiManager = PsiManager.getInstance(project)
        val processor = FileBasedIndex.ValueProcessor<String> { file, _ ->
            val yamlFile = psiManager.findFile(file) ?: return@ValueProcessor true
            serviceDefinition = YAMLKeyValueFinder("services.$serviceId").findIn(yamlFile)
            serviceDefinition == null
        }

        FileBasedIndex.getInstance()
            .processValues(SERVICES_INDEX_KEY, serviceId, null, processor, GlobalSearchScope.allScope(project))

        return serviceDefinition
    }

    private fun serviceFiles(project: Project): List<YAMLFile> {
        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)

        return index.getAllProjectKeys(SERVICES_INDEX_KEY, project)
            .flatMap { index.getContainingFiles(SERVICES_INDEX_KEY, it, scope) }
            .distinct()
            .mapNotNull { psiManager.findFile(it) as? YAMLFile }
    }
}
