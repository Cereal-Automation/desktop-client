package com.cereal.client.domain.model.datasets

import com.cereal.client.domain.model.script.configuration.ConfigValue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustomDatasetItemTest {
    @Test
    fun `should create valid CustomDatasetItem with single field`() {
        val id = UUID.randomUUID()
        val fields = mapOf("key1" to ConfigValue.StringValue("value1"))
        val item =
            CustomDatasetItem(
                id = id,
                fields = fields,
            )

        assertEquals(id, item.id)
        assertEquals(fields, item.fields)
        assertEquals(ConfigValue.StringValue("value1"), item.fields["key1"])
    }

    @Test
    fun `should create valid CustomDatasetItem with multiple fields`() {
        val id = UUID.randomUUID()
        val fields =
            mapOf(
                "key1" to ConfigValue.StringValue("value1"),
                "key2" to ConfigValue.IntValue(42),
                "key3" to ConfigValue.BooleanValue(true),
                "key4" to null,
            )
        val item =
            CustomDatasetItem(
                id = id,
                fields = fields,
            )

        assertEquals(id, item.id)
        assertEquals(4, item.fields.size)
        assertEquals(ConfigValue.StringValue("value1"), item.fields["key1"])
        assertEquals(ConfigValue.IntValue(42), item.fields["key2"])
        assertEquals(ConfigValue.BooleanValue(true), item.fields["key3"])
        assertEquals(null, item.fields["key4"])
    }

    @Test
    fun `should create valid CustomDatasetItem with float field`() {
        val id = UUID.randomUUID()
        val floatValue = ConfigValue.FloatValue(1.5f)
        val fields =
            mapOf(
                "key1" to ConfigValue.StringValue("value1"),
                "ratio" to floatValue,
            )
        val item =
            CustomDatasetItem(
                id = id,
                fields = fields,
            )

        assertEquals(id, item.id)
        assertEquals(floatValue, item.fields["ratio"])
    }

    @Test
    fun `should throw exception when fields map is empty`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                CustomDatasetItem(
                    id = UUID.randomUUID(),
                    fields = emptyMap(),
                )
            }
        assertEquals("Fields map must not be empty", exception.message)
    }

    @Test
    fun `should accept fields with null values`() {
        val id = UUID.randomUUID()
        val fields =
            mapOf(
                "key1" to ConfigValue.StringValue("value1"),
                "key2" to null,
            )
        val item =
            CustomDatasetItem(
                id = id,
                fields = fields,
            )

        assertEquals(2, item.fields.size)
        assertEquals(null, item.fields["key2"])
    }

    @Test
    fun `should accept fields with various types`() {
        val id = UUID.randomUUID()
        val fields =
            mapOf(
                "stringField" to ConfigValue.StringValue("test"),
                "intField" to ConfigValue.IntValue(123),
                "doubleField" to ConfigValue.DoubleValue(45.67),
                "booleanField" to ConfigValue.BooleanValue(true),
                "floatField" to ConfigValue.FloatValue(1.5f),
                "nullField" to null,
            )
        val item =
            CustomDatasetItem(
                id = id,
                fields = fields,
            )

        assertEquals(6, item.fields.size)
        assertEquals(ConfigValue.StringValue("test"), item.fields["stringField"])
        assertEquals(ConfigValue.IntValue(123), item.fields["intField"])
        assertEquals(ConfigValue.DoubleValue(45.67), item.fields["doubleField"])
        assertEquals(ConfigValue.BooleanValue(true), item.fields["booleanField"])
        assertEquals(ConfigValue.FloatValue(1.5f), item.fields["floatField"])
        assertEquals(null, item.fields["nullField"])
    }
}
