package com.github.nvelychenko.drupalextend.index.types

import kotlinx.serialization.Serializable

/**
 * One service factory method, indexed by [methodName].
 *
 * A single `*.services.yml` file can declare several services whose factory uses the same
 * method name but a different owner (e.g. two services both with `factory: Foo::create`).
 * Because a [com.intellij.util.indexing.FileBasedIndexExtension] indexer produces a
 * `Map<String, DrupalServiceFactory>` keyed by the index key (the method name), the owners
 * for a colliding method name are collected into [owners] so none are lost.
 */
@Serializable
data class DrupalServiceFactory(
    val methodName: String,
    val owners: List<DrupalServiceFactoryOwner> = emptyList(),
)

@Serializable
data class DrupalServiceFactoryOwner(
    val ownerClassFqn: String? = null,
    val ownerServiceId: String? = null,
)
