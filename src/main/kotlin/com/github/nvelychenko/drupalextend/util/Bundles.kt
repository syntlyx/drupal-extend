package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getValue
import com.github.nvelychenko.drupalextend.index.CONTENT_ENTITY_FQN_INDEX_KEY
import com.github.nvelychenko.drupalextend.index.CONTENT_ENTITY_INDEX_KEY
import com.github.nvelychenko.drupalextend.project.drupalExtendSettings
import com.github.nvelychenko.drupalextend.type.EntityQueryTypeProvider
import com.github.nvelychenko.drupalextend.type.EntityStorageTypeProvider
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Bundle ids of an entity type, derived from the bundle config entities already present in
 * [com.github.nvelychenko.drupalextend.index.ConfigObjectIndex] (`node.type.article` → bundle
 * `article` of `node`). The entity-type → config-prefix mapping is curated for core entity
 * types; custom bundleable entities can be added once the provider/config_prefix chain is
 * indexed.
 */
object Bundles {

    private val BUNDLE_CONFIG_PREFIXES = mapOf(
        "node" to "node.type",
        "taxonomy_term" to "taxonomy.vocabulary",
        "media" to "media.type",
        "block_content" to "block_content.type",
        "comment" to "comment.type",
        "paragraph" to "paragraphs.paragraphs_type",
        "contact_message" to "contact.form",
        "profile" to "profile.type",
    )

    /** Methods that create a known entity type regardless of the receiver. */
    private val CREATION_METHOD_ENTITY_TYPES = mapOf(
        "drupalCreateNode" to "node",
    )

    private val ARRAY_VALUE_METHODS = setOf("create", "loadByProperties")

    fun bundleConfigPrefix(entityTypeId: String): String? = BUNDLE_CONFIG_PREFIXES[entityTypeId]

    fun bundleIds(project: Project, entityTypeId: String): List<String> {
        val prefix = bundleConfigPrefix(entityTypeId) ?: return emptyList()

        return Configs.allNames(project)
            .filter { it.startsWith("$prefix.") }
            .map { it.removePrefix("$prefix.") }
            .filter { it.isNotBlank() && '.' !in it }
    }

    fun bundleConfigName(entityTypeId: String, bundleId: String): String? {
        return bundleConfigPrefix(entityTypeId)?.let { "$it.$bundleId" }
    }

    /** The entity-key field holding the bundle (`type` for nodes, `vid` for terms), or null. */
    fun bundleKey(project: Project, entityTypeId: String): String? {
        return FileBasedIndex.getInstance()
            .getValue(CONTENT_ENTITY_INDEX_KEY, entityTypeId, project)
            ?.keys
            ?.get("bundle")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * When [literal] sits in a bundle-value position, returns the entity type id it belongs
     * to; null otherwise. Recognized shapes:
     *  - `entityQuery('node')->condition('type', '<literal>')` (and storage `getQuery()` chains)
     *  - `Node::create(['type' => '<literal>'])` / `$storage->create([...])`
     *  - `$storage->loadByProperties(['type' => '<literal>'])`
     *  - `$this->drupalCreateNode(['type' => '<literal>'])` (test trait)
     */
    fun bundleValueEntityType(literal: StringLiteralExpression): String? {
        if (!literal.project.drupalExtendSettings.isEnabled) return null

        return conditionValueEntityType(literal) ?: arrayValueEntityType(literal)
    }

    private fun conditionValueEntityType(literal: StringLiteralExpression): String? {
        val parameterList = literal.parent as? ParameterList ?: return null
        val methodReference = parameterList.parent as? MethodReference ?: return null
        if (methodReference.name != "condition") return null
        if (parameterList.parameters.getOrNull(1)?.isEquivalentTo(literal) != true) return null

        val entityTypeId = entityTypeFromSignature(methodReference.signature) ?: return null
        val bundleKey = bundleKey(literal.project, entityTypeId) ?: return null
        val fieldArgument = parameterList.parameters.getOrNull(0) as? StringLiteralExpression ?: return null
        if (fieldArgument.contents != bundleKey) return null

        return entityTypeId
    }

    private fun arrayValueEntityType(literal: StringLiteralExpression): String? {
        // The hash value is wrapped in an `Array value` composite in the PHP PSI.
        val hashElement = literal.parent as? ArrayHashElement
            ?: literal.parent?.parent as? ArrayHashElement
            ?: return null
        if (hashElement.value?.isEquivalentTo(literal) != true) return null
        val keyLiteral = hashElement.key as? StringLiteralExpression ?: return null

        val array = hashElement.parent ?: return null
        val parameterList = array.parent as? ParameterList ?: return null
        val methodReference = parameterList.parent as? MethodReference ?: return null
        val methodName = methodReference.name ?: return null

        val entityTypeId = CREATION_METHOD_ENTITY_TYPES[methodName]
            ?: methodName.takeIf { it in ARRAY_VALUE_METHODS }?.let { entityTypeFor(methodReference) }
            ?: return null
        val bundleKey = bundleKey(literal.project, entityTypeId) ?: return null
        if (keyLiteral.contents != bundleKey) return null

        return entityTypeId
    }

    /** Entity type of a `create`/`loadByProperties` call: storage chain marker or static class. */
    private fun entityTypeFor(methodReference: MethodReference): String? {
        entityTypeFromSignature(methodReference.signature)?.let { return it }

        val classReference = methodReference.classReference as? ClassReference ?: return null
        val fqn = classReference.fqn ?: return null

        return FileBasedIndex.getInstance()
            .getValue(CONTENT_ENTITY_FQN_INDEX_KEY, fqn, methodReference.project)
            ?.entityTypeId
    }

    /** Extracts the entity type id from the query/storage type-provider markers, if present. */
    private fun entityTypeFromSignature(signature: String): String? {
        if (EntityQueryTypeProvider.KEY in signature) {
            return signature.substringAfter(EntityQueryTypeProvider.KEY)
                .substringBefore(EntityQueryTypeProvider.END_KEY)
                .takeIf { it.isNotBlank() && EntityQueryTypeProvider.KEY !in it }
        }
        if (EntityStorageTypeProvider.Util.SPLIT_KEY in signature) {
            return signature.substringAfter(EntityStorageTypeProvider.Util.SPLIT_KEY)
                .substringBefore('.')
                .substringBefore('|')
                .takeIf { it.isNotBlank() }
        }

        return null
    }
}
