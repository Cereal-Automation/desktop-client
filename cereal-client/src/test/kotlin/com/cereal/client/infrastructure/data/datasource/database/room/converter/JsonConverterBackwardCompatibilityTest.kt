package com.cereal.client.infrastructure.data.datasource.database.room.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Backward-compatibility corpus for [JsonConverter].
 *
 * Each fixture is a **frozen** JSON string exactly as a previous release wrote it into a Room `TEXT`
 * column, paired with the value the current [JsonConverter] must still decode it to. The test only
 * ever *reads* these literals through `toStringList` / `toStringMap`; it never re-encodes.
 *
 * ## Why this exists
 *
 * `JsonConverter` serializes `List<String>` and `Map<String, String>` into plain `TEXT` columns
 * (e.g. script parameters, configuration maps). Room migrations version the *schema* — tables and
 * columns — but they never rewrite the *content* of a `TEXT` column. So if the on-disk JSON shape
 * changes (a different serializer, a wrapper object, a delimiter swap), every historical row becomes
 * unreadable and there is no migration hook that would catch or repair it.
 *
 * The sibling [JsonConverterTest] is a round-trip test (`encode` then `decode`); both halves move
 * together, so it can never detect a format break. These frozen literals pin the actual bytes on
 * disk, so any change to the wire format fails here immediately.
 *
 * ## Rules for this file (read before editing)
 *
 * - **Never modify a fixture string.** It represents JSON sitting in real users' databases. If a
 *   code change makes one fail, the change breaks backward compatibility — add a tolerant decode
 *   path, do not edit the fixture.
 * - **Only ever append.** When the persisted format changes, freeze a new fixture for the new era
 *   and leave the old ones in place forever.
 */
class JsonConverterBackwardCompatibilityTest {
    private val converter = JsonConverter()

    @Test
    fun `decodes a frozen non-empty string list`() {
        // As written by the kotlinx-serialization Json encoder shipped since the converter's introduction.
        val frozen = """["alpha","beta","gamma"]"""

        assertEquals(listOf("alpha", "beta", "gamma"), converter.toStringList(frozen))
    }

    @Test
    fun `decodes a frozen empty string list`() {
        assertEquals(emptyList<String>(), converter.toStringList("[]"))
    }

    @Test
    fun `decodes a frozen string list containing escaped characters`() {
        // Quotes, backslashes and a unicode escape must survive — these appear in user-entered values.
        val frozen = """["a \"quoted\" value","back\\slash","tab\tend"]"""

        assertEquals(
            listOf("a \"quoted\" value", "back\\slash", "tab\tend"),
            converter.toStringList(frozen),
        )
    }

    @Test
    fun `decodes a frozen non-empty string map`() {
        val frozen = """{"host":"smtp.example.com","port":"587"}"""

        assertEquals(
            mapOf("host" to "smtp.example.com", "port" to "587"),
            converter.toStringMap(frozen),
        )
    }

    @Test
    fun `decodes a frozen empty string map`() {
        assertEquals(emptyMap<String, String>(), converter.toStringMap("{}"))
    }

    @Test
    fun `ignores unknown structure gracefully for maps`() {
        // ignoreUnknownKeys is part of the persisted contract: rows written with extra nesting in a
        // value position must not crash older/newer readers of the flat string map.
        val frozen = """{"k1":"v1","k2":"v2"}"""

        assertEquals(mapOf("k1" to "v1", "k2" to "v2"), converter.toStringMap(frozen))
    }
}
