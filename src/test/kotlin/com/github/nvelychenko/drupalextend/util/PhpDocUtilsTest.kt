package com.github.nvelychenko.drupalextend.util

import junit.framework.TestCase

class PhpDocUtilsTest : TestCase() {

    fun testMatchesExactKey() {
        assertEquals("node", getPhpDocParameter("""* id = "node",""", "id"))
    }

    fun testDoesNotMatchKeySuffix() {
        // `field_id` must not satisfy a lookup for `id`.
        assertNull(getPhpDocParameter("""* field_id = "foo",""", "id"))
    }

    fun testDoesNotMatchKeyEmbeddedInAnotherWord() {
        // `valid` ends with `id` and must not satisfy a lookup for `id`.
        assertNull(getPhpDocParameter("""* valid = "yes",""", "id"))
    }

    fun testLastCharacterOfKeyIsNotOptional() {
        // The old pattern made the last character of the key optional, so `i = "..."` matched `id`.
        assertNull(getPhpDocParameter("""* i = "broken",""", "id"))
    }

    fun testSkipsQuotedEntityKeyForBareId() {
        val doc = """
            * entity_keys = {
            *   "id" = "nid",
            * },
            * id = "node",
        """.trimIndent()
        assertEquals("node", getPhpDocParameter(doc, "id"))
    }

    fun testMatchesQuotedKeyWhenRequested() {
        assertEquals("nid", getPhpDocParameter("""* "id" = "nid",""", "\"id\""))
    }

    fun testRegexMetacharactersInKeyAreEscaped() {
        assertNull(getPhpDocParameter("""* idx = "foo",""", "id."))
    }
}
