package com.cereal.client.infrastructure.data.datasource.database.room.converter

import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ValueTypeConverterTest {
    private val converter = ValueTypeConverter()

    @Test
    fun `every value type round-trips`() {
        ValueType.entries.forEach { valueType ->
            assertEquals(valueType, converter.toValueType(converter.fromValueType(valueType)))
        }
    }

    @Test
    fun `a retired type label reads as unknown rather than throwing`() {
        // STRING_LIST rows survive in script_parameter after the 12 -> 13 migration, which leaves that
        // table alone. Room's generated enum mapping would throw on them and take down every read of
        // the table; this converter is why one leftover row degrades to "no value" instead.
        assertEquals(ValueType.UNKNOWN, converter.toValueType("STRING_LIST"))
        assertEquals(ValueType.UNKNOWN, converter.toValueType("OBJECT_LIST"))
        assertEquals(ValueType.UNKNOWN, converter.toValueType("a label no release ever wrote"))
    }

    @Test
    fun `null survives in both directions`() {
        assertNull(converter.fromValueType(null))
        assertNull(converter.toValueType(null))
    }
}
