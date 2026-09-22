package com.cereal.client.infrastructure.data.datasource.database.room.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pure round-trip unit test for [JsonConverter] (the "Infra: mappers" boundary in AGENTS.md).
 */
class JsonConverterTest {
    private val converter = JsonConverter()

    @Test
    fun `string list round-trips`() {
        val list = listOf("a", "b", "c")

        val encoded = converter.fromStringList(list)
        val decoded = converter.toStringList(encoded)

        assertEquals(list, decoded)
    }

    @Test
    fun `empty string list round-trips`() {
        val encoded = converter.fromStringList(emptyList())

        assertEquals(emptyList<String>(), converter.toStringList(encoded))
    }

    @Test
    fun `string map round-trips`() {
        val map = mapOf("k1" to "v1", "k2" to "v2")

        val encoded = converter.fromStringMap(map)
        val decoded = converter.toStringMap(encoded)

        assertEquals(map, decoded)
    }

    @Test
    fun `null values convert to null in both directions`() {
        assertNull(converter.fromStringList(null))
        assertNull(converter.toStringList(null))
        assertNull(converter.fromStringMap(null))
        assertNull(converter.toStringMap(null))
    }
}
