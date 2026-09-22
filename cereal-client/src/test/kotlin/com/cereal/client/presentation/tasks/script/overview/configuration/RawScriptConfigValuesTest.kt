package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.sdk.statemodifier.ScriptConfigValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.time.ExperimentalTime

class RawScriptConfigValuesTest {
    @Test
    fun `converts each supported config value to its script config value`() {
        val config =
            RawScriptConfigValues(
                mapOf(
                    "double" to ConfigValue.DoubleValue(1.5),
                    "int" to ConfigValue.IntValue(7),
                    "boolean" to ConfigValue.BooleanValue(true),
                    "string" to ConfigValue.StringValue("hello"),
                    "float" to ConfigValue.FloatValue(2.5f),
                    "enum" to ConfigValue.EnumValue(Sample.A),
                    "proxyGroup" to ConfigValue.ProxyGroupValue(ProxyGroup("g", "Group", 3, emptySequence())),
                ),
            )

        assertEquals(1.5, (config.valueForKey("double") as ScriptConfigValue.DoubleScriptConfigValue).value)
        assertEquals(7, (config.valueForKey("int") as ScriptConfigValue.IntScriptConfigValue).value)
        assertEquals(true, (config.valueForKey("boolean") as ScriptConfigValue.BooleanScriptConfigValue).value)
        assertEquals("hello", (config.valueForKey("string") as ScriptConfigValue.StringScriptConfigValue).value)
        assertEquals(2.5f, (config.valueForKey("float") as ScriptConfigValue.FloatScriptConfigValue).value)
        assertTrue(config.valueForKey("enum") is ScriptConfigValue.EnumScriptConfigValue)
        assertEquals("g", (config.valueForKey("proxyGroup") as ScriptConfigValue.ProxyGroupScriptConfigValue).value.id)
    }

    @Test
    fun `returns null script config value for an unknown key`() {
        val config = RawScriptConfigValues(emptyMap())

        assertEquals(ScriptConfigValue.NullScriptConfigValue, config.valueForKey("missing"))
    }

    @Test
    fun `throws for an unsupported config value type`() {
        val config = RawScriptConfigValues(mapOf("long" to ConfigValue.LongValue(5L)))

        assertThrows<RuntimeException> { config.valueForKey("long") }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `resolves dataset field keys to a sequence of values`() {
        val fieldDefinition =
            ScriptConfigurationItemDefinition(
                name = "Email",
                description = "Email column",
                key = "email",
                position = 0,
                type = ConfigItemType.StringConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            )
        val group =
            CustomDatasetGroup(
                id = "ds",
                name = "Dataset",
                numberOfItems = 2,
                itemDefinitions = listOf(fieldDefinition),
                items =
                    sequenceOf(
                        CustomDatasetItem(UUID.randomUUID(), mapOf("email" to ConfigValue.StringValue("a@b.com"))),
                        CustomDatasetItem(UUID.randomUUID(), mapOf<String, ConfigValue?>("email" to null)),
                    ),
                createdAt =
                    kotlin.time.Clock.System
                        .now(),
            )
        val config =
            RawScriptConfigValues(
                mapOf(ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key to ConfigValue.CustomDatasetGroupValue(group)),
            )

        val sequence = config.valueForKey("email") as ScriptConfigValue.SequenceScriptConfigValue
        val values = sequence.values.toList()

        assertEquals(2, values.size)
        assertTrue(values.first() is ScriptConfigValue.StringScriptConfigValue)
        assertEquals(ScriptConfigValue.NullScriptConfigValue, values[1])
    }

    // region Secret

    @Test
    fun `converts a secret value to the secret script config value`() {
        val config = RawScriptConfigValues(mapOf("apiKey" to ConfigValue.SecretValue(Secret("sk-live-token"))))

        val value = config.valueForKey("apiKey")

        assertTrue(value is ScriptConfigValue.SecretScriptConfigValue)
        assertEquals("sk-live-token", (value as ScriptConfigValue.SecretScriptConfigValue).value.reveal())
    }

    @Test
    fun `does not throw for a secret value, so the required-field check cannot crash the screen`() {
        // The conversion throws on unrecognised types and runs on every keystroke through the
        // required-field check. Without the secret branch, a secret field crashes the configuration
        // screen the moment it exists — this test is the guard on that.
        val config = RawScriptConfigValues(mapOf("apiKey" to ConfigValue.SecretValue(Secret("sk-live-token"))))

        assertDoesNotThrow { config.valueForKey("apiKey") }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `resolves a per-task secret to a sequence of secret values`() {
        val fieldDefinition =
            ScriptConfigurationItemDefinition(
                name = "API key",
                description = "Credential column",
                key = "apiKey",
                position = 0,
                type = ConfigItemType.SecretConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            )
        val group =
            CustomDatasetGroup(
                id = "ds",
                name = "Dataset",
                numberOfItems = 2,
                itemDefinitions = listOf(fieldDefinition),
                items =
                    sequenceOf(
                        CustomDatasetItem(UUID.randomUUID(), mapOf("apiKey" to ConfigValue.SecretValue(Secret("first")))),
                        CustomDatasetItem(UUID.randomUUID(), mapOf("apiKey" to ConfigValue.SecretValue(Secret("second")))),
                    ),
                createdAt =
                    kotlin.time.Clock.System
                        .now(),
            )
        val config =
            RawScriptConfigValues(
                mapOf(ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key to ConfigValue.CustomDatasetGroupValue(group)),
            )

        val values = (config.valueForKey("apiKey") as ScriptConfigValue.SequenceScriptConfigValue).values.toList()

        assertEquals(2, values.size)
        assertEquals(
            listOf("first", "second"),
            values.map { (it as ScriptConfigValue.SecretScriptConfigValue).value.reveal() },
        )
    }

    // endregion
    @Test
    fun `exposes a list as nested configuration views, one per row`() {
        val config =
            RawScriptConfigValues(
                mapOf(
                    "targets" to
                        ConfigValue.ListValue(
                            ListRows(
                                listOf(
                                    ListRow(
                                        mapOf(
                                            "sku" to ConfigValue.StringValue("ABC-123"),
                                            "qty" to ConfigValue.IntValue(2),
                                        ),
                                    ),
                                    ListRow(mapOf("sku" to ConfigValue.StringValue("XYZ-9"))),
                                ),
                            ),
                        ),
                ),
            )

        val rows = (config.valueForKey("targets") as ScriptConfigValue.ListScriptConfigValue).items

        assertEquals(2, rows.size)
        assertEquals("ABC-123", (rows[0].valueForKey("sku") as ScriptConfigValue.StringScriptConfigValue).value)
        assertEquals(2, (rows[0].valueForKey("qty") as ScriptConfigValue.IntScriptConfigValue).value)
        assertEquals("XYZ-9", (rows[1].valueForKey("sku") as ScriptConfigValue.StringScriptConfigValue).value)
        // A field the user left blank is absent from the row, so it reads back as unset.
        assertEquals(ScriptConfigValue.NullScriptConfigValue, rows[1].valueForKey("qty"))
    }

    @Test
    fun `exposes an empty list as zero rows`() {
        val config = RawScriptConfigValues(mapOf("targets" to ConfigValue.ListValue(ListRows.EMPTY)))

        val rows = (config.valueForKey("targets") as ScriptConfigValue.ListScriptConfigValue).items

        assertTrue(rows.isEmpty())
    }

    private enum class Sample { A, B }
}
