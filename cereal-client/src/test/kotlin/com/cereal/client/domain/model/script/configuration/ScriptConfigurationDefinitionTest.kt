package com.cereal.client.domain.model.script.configuration

import com.cereal.sdk.ScriptConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptConfigurationDefinitionTest {
    private fun item(
        key: String,
        type: ConfigItemType = ConfigItemType.StringConfigItem,
        isNullable: Boolean = false,
        isScriptIdentifier: Boolean = false,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "",
        key = key,
        position = 0,
        type = type,
        isNullable = isNullable,
        stateModifier = null,
        isScriptIdentifier = isScriptIdentifier,
    )

    private fun definition(items: List<ScriptConfigurationItemDefinition>) =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = items,
        )

    @Test
    fun `itemForKey returns the matching item or null`() {
        val target = item("name")
        val definition = definition(listOf(item("age"), target))

        assertSame(target, definition.itemForKey("name"))
        assertNull(definition.itemForKey("missing"))
    }

    @Test
    fun `proxyConfigurationDefinition returns the proxy item when present`() {
        val proxy = item("proxy", type = ConfigItemType.ProxyConfigItem)
        val definition = definition(listOf(item("name"), proxy))

        assertSame(proxy, definition.proxyConfigurationDefinition())
        assertNull(definition(listOf(item("name"))).proxyConfigurationDefinition())
    }

    @Test
    fun `groupedConfigurationDefinition returns the grouped item when present`() {
        val grouped = item("group", type = ConfigItemType.GroupedConfigItem(items = emptyList()))
        val definition = definition(listOf(item("name"), grouped))

        assertSame(grouped, definition.groupedConfigurationDefinition())
        assertNull(definition(listOf(item("name"))).groupedConfigurationDefinition())
    }

    @Test
    fun `getScriptIdentifierValue returns the value of the identifier item`() {
        val definition =
            definition(
                listOf(
                    item("name"),
                    item("id", isScriptIdentifier = true),
                ),
            )
        val values =
            mapOf(
                "name" to ConfigValue.StringValue("Alice"),
                "id" to ConfigValue.StringValue("abc-123"),
            )

        assertEquals("abc-123", definition.getScriptIdentifierValue(values))
    }

    @Test
    fun `getScriptIdentifierValue returns null when there is no identifier item`() {
        val definition = definition(listOf(item("name")))

        assertNull(definition.getScriptIdentifierValue(mapOf("name" to ConfigValue.StringValue("Alice"))))
    }

    @Test
    fun `compareTo flags a missing non-nullable item`() {
        val current = listOf(item("required", isNullable = false))

        val invalid = current.compareTo(expectedDefinitions = emptyList())

        assertEquals(listOf(item("required", isNullable = false)), invalid)
    }

    @Test
    fun `compareTo ignores a missing nullable item`() {
        val current = listOf(item("optional", isNullable = true))

        assertTrue(current.compareTo(expectedDefinitions = emptyList()).isEmpty())
    }

    @Test
    fun `compareTo flags an item whose type does not match`() {
        val current = listOf(item("field", type = ConfigItemType.StringConfigItem))
        val expected = listOf(item("field", type = ConfigItemType.IntConfigItem))

        val invalid = current.compareTo(expected)

        assertEquals(1, invalid.size)
        assertEquals("field", invalid.first().key)
    }

    @Test
    fun `compareTo accepts an item whose type matches`() {
        val current = listOf(item("field", type = ConfigItemType.StringConfigItem))
        val expected = listOf(item("field", type = ConfigItemType.StringConfigItem))

        assertTrue(current.compareTo(expected).isEmpty())
    }
}
