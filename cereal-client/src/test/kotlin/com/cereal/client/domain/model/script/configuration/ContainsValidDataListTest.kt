package com.cereal.client.domain.model.script.configuration

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The check that stops a script from starting while a list is incomplete.
 */
class ContainsValidDataListTest {
    private fun fieldDefinition(
        key: String,
        isNullable: Boolean,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc",
        key = key,
        position = 0,
        type = ConfigItemType.StringConfigItem,
        isNullable = isNullable,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    private val definition =
        ScriptConfigurationItemDefinition(
            name = "Targets",
            description = "Products to purchase",
            key = "targets",
            position = 0,
            type =
                ConfigItemType.ListConfigItem(
                    itemType = String::class,
                    items =
                        listOf(
                            fieldDefinition("sku", isNullable = false),
                            fieldDefinition("note", isNullable = true),
                        ),
                ),
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private fun rows(vararg rows: Map<ConfigKey, ConfigValue>) = ConfigValue.ListValue(ListRows(rows.map { ListRow(it) }))

    @Test
    fun `containsValidData should return true when every row supplies each non-nullable field`() {
        assertTrue(
            definition.containsValidData(
                rows(
                    mapOf("sku" to ConfigValue.StringValue("ABC-123"), "note" to ConfigValue.StringValue("hurry")),
                    mapOf("sku" to ConfigValue.StringValue("XYZ-9")),
                ),
            ),
        )
    }

    @Test
    fun `containsValidData should return false when a row is missing a non-nullable field`() {
        assertFalse(
            definition.containsValidData(
                rows(
                    mapOf("sku" to ConfigValue.StringValue("ABC-123")),
                    mapOf("note" to ConfigValue.StringValue("hurry")),
                ),
            ),
        )
    }

    @Test
    fun `containsValidData should return false when there are no rows`() {
        assertFalse(definition.containsValidData(ConfigValue.ListValue(ListRows.EMPTY)))
    }

    @Test
    fun `isValidReturnType should accept a list value and reject any other value`() {
        assertTrue(definition.isValidReturnType(rows(mapOf("sku" to ConfigValue.StringValue("ABC-123")))))
        assertFalse(definition.isValidReturnType(ConfigValue.StringValue("ABC-123")))
    }
}
