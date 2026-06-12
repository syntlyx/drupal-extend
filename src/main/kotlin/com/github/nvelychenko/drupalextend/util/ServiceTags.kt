package com.github.nvelychenko.drupalextend.util

import com.github.nvelychenko.drupalextend.extensions.getAllProjectKeys
import com.github.nvelychenko.drupalextend.index.SERVICE_TAGS_INDEX_KEY
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

/**
 * Service-tag lookups backing tag-name completion under `tags:` and `!tagged_iterator` /
 * `!tagged_locator` completion and validation. Tag names come from [SERVICE_TAGS_INDEX_KEY]
 * (tags actually used anywhere in the project, including core/contrib) unioned with
 * [ServicesYaml.curatedCoreTags] so standard tags complete and validate even before any service
 * uses them. Navigation between tag sites goes through the usages flow (references search +
 * find-usages handler), not through lookups here.
 */
object ServiceTags {

    /** Every tag name worth completing: indexed (in-project) tags plus the curated core list. */
    fun allTagNames(project: Project): List<String> {
        if (DumbService.getInstance(project).isDumb) return ServicesYaml.curatedCoreTags.sorted()

        return (FileBasedIndex.getInstance().getAllProjectKeys(SERVICE_TAGS_INDEX_KEY, project)
                + ServicesYaml.curatedCoreTags)
            .distinct()
            .sorted()
    }

    /**
     * Whether [tagName] is known: declared by some service in the index or present in the curated
     * core list. Consumer-only index entries (a `!tagged_iterator` usage with no declaring
     * service) deliberately don't count — otherwise the very usage being validated would make
     * its own tag "known". Used by the inspection — a `false` result is reported only as a weak
     * warning, since the tag may belong to a module that is not currently installed.
     */
    fun isKnown(project: Project, tagName: String): Boolean {
        if (ServicesYaml.curatedCoreTags.contains(tagName)) return true
        if (DumbService.getInstance(project).isDumb) return true

        return FileBasedIndex.getInstance()
            .getValues(SERVICE_TAGS_INDEX_KEY, tagName, GlobalSearchScope.allScope(project))
            .any { it.serviceIds.isNotEmpty() }
    }

}
