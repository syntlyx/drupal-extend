package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.index.*
import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.extensions.getModificationTrackerForIndexId
import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.types.DrupalServiceFactoryOwner
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.util.yml.YAMLKeyValueFinder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.FileBasedIndex
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

        return classFqn.normalizeServiceClassFqn() in serviceClassFqns(project)
    }

    /**
     * Normalized FQNs of every class referenced by a service definition (service classes plus
     * FQN-style service ids). [referencesClass] is called for every class on every highlighting
     * pass, so the set is cached against the services index and rebuilt only when a
     * `*.services.yml` file changes — never on PHP edits.
     */
    private fun serviceClassFqns(project: Project): Set<String> {
        return CachedValuesManager.getManager(project).getCachedValue(project, SERVICE_CLASS_FQNS_KEY, {
            val index = FileBasedIndex.getInstance()
            val scope = GlobalSearchScope.allScope(project)
            val fqns = hashSetOf<String>()
            for (serviceId in index.getAllProjectKeys(SERVICES_INDEX_KEY, project)) {
                ProgressManager.checkCanceled()
                fqns.add(serviceId.normalizeServiceClassFqn())
                index.getValues(SERVICES_INDEX_KEY, serviceId, scope)
                    .forEach { fqns.add(it.normalizeServiceClassFqn()) }
            }
            CachedValueProvider.Result.create(
                fqns,
                getModificationTrackerForIndexId(project, SERVICES_INDEX_KEY, index)
            )
        }, false)
    }

    fun referencesMethod(project: Project, classFqn: String, methodName: String): Boolean {
        if (!isSupportEnabled(project)) return false

        val expectedClass = classFqn.normalizeServiceClassFqn()

        return FileBasedIndex.getInstance()
            .getValues(SERVICE_FACTORY_INDEX_KEY, methodName, GlobalSearchScope.allScope(project))
            .flatMap { it.owners }
            .any { owner ->
                factoryOwnerClassName(project, owner)?.normalizeServiceClassFqn() == expectedClass
            }
    }

    fun factoryOwnerClassName(project: Project, owner: DrupalServiceFactoryOwner): String? {
        owner.ownerClassFqn?.let { return it }

        val ownerServiceId = owner.ownerServiceId ?: return null
        return className(project, ownerServiceId)?.takeIf { it.isNotBlank() }
    }

    fun factoryOwnerClassName(project: Project, factoryMethod: ServicesYaml.ServiceFactoryMethod): String? {
        return factoryOwnerClassName(
            project,
            DrupalServiceFactoryOwner(factoryMethod.ownerClassFqn, factoryMethod.ownerServiceId),
        )
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
}

private val SERVICE_CLASS_FQNS_KEY: Key<CachedValue<Set<String>>> =
    Key.create("com.github.nvelychenko.drupalextend.util.Services.serviceClassFqns")
