package com.cereal.client.presentation.tasks.dialog

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.Secret
import fixtures.anAgentItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigurationItemDisplayValueTest {
    private val labelField = anAgentItem("label", ConfigItemType.StringConfigItem, 0).copy(name = "Label")
    private val valueField = anAgentItem("value", ConfigItemType.SecretConfigItem, 1).copy(name = "Value")
    private val secretsDefinition =
        anAgentItem("secrets", ConfigItemType.ListConfigItem(Any::class, listOf(labelField, valueField)), 0)

    private fun row(
        label: String,
        secret: String,
    ) = ListRow(mapOf("label" to ConfigValue.StringValue(label), "value" to ConfigValue.SecretValue(Secret(secret))))

    @Test
    fun `list renders one row per line with field names and masked secrets`() {
        val item =
            ConfigurationItem(
                secretsDefinition,
                ConfigValue.ListValue(ListRows(listOf(row("username", "bob"), row("password", "hunter2")))),
            )

        assertEquals(
            "Label: username · Value: ***\nLabel: password · Value: ***",
            item.displayValue(),
        )
    }

    @Test
    fun `scalar renders its raw value`() {
        val item = ConfigurationItem(anAgentItem("maxSteps", ConfigItemType.IntConfigItem, 0), ConfigValue.IntValue(40))

        assertEquals("40", item.displayValue())
    }
}
