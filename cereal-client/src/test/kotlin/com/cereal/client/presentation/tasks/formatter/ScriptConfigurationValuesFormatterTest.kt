package com.cereal.client.presentation.tasks.formatter

import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.sdk.ScriptConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ScriptConfigurationValuesFormatterTest {
    private fun definition(vararg items: Pair<String, String>) =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems =
                items.mapIndexed { index, (key, name) ->
                    ScriptConfigurationItemDefinition(
                        name = name,
                        description = "desc",
                        key = key,
                        position = index,
                        type = ConfigItemType.StringConfigItem,
                        isNullable = true,
                        stateModifier = null,
                        isScriptIdentifier = false,
                    )
                },
        )

    @Test
    fun `formats values using the item display names`() {
        val values =
            mapOf<String, ConfigValue>(
                "name" to ConfigValue.StringValue("Bob"),
                "count" to ConfigValue.IntValue(3),
            )

        val result = values.joinToString(definition("name" to "Name", "count" to "Count"))

        assertEquals("Name: Bob - Count: 3", result)
    }

    @Test
    fun `compact mode omits the keys`() {
        val values =
            mapOf<String, ConfigValue>(
                "name" to ConfigValue.StringValue("Bob"),
                "count" to ConfigValue.IntValue(3),
            )

        val result = values.joinToString(definition("name" to "Name", "count" to "Count"), compact = true)

        assertEquals("Bob - 3", result)
    }

    @Test
    fun `falls back to the raw key when there is no matching item definition`() {
        val values = mapOf<String, ConfigValue>("unknown" to ConfigValue.StringValue("v"))

        val result = values.joinToString(definition(), separator = " | ")

        assertEquals("unknown: v", result)
    }

    @Test
    fun `formats a proxy value as address and port`() {
        val proxy = Proxy(UUID.randomUUID(), "127.0.0.1", 8080, null, null)
        val values = mapOf<String, ConfigValue>("proxy" to ConfigValue.ProxyValue(proxy))

        val result = values.joinToString(definition("proxy" to "Proxy"))

        assertEquals("Proxy: 127.0.0.1:8080", result)
    }

    @Test
    fun `appends custom dataset item fields and skips the dataset entry itself`() {
        val item = CustomDatasetItem(UUID.randomUUID(), mapOf("email" to ConfigValue.StringValue("a@b.com")))
        val values =
            mapOf<String, ConfigValue>(
                ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key to ConfigValue.CustomDatasetItemValue(item),
            )

        val result = values.joinToString(definition("email" to "Email"))

        assertTrue(result.contains("Email: a@b.com"), result)
    }

    // region Secret

    @Test
    fun `renders a configured secret as the mask, never the credential`() {
        val values = mapOf<String, ConfigValue>("apiKey" to ConfigValue.SecretValue(Secret("sk-live-token")))

        val result = values.joinToString(definition("apiKey" to "API key"))

        assertEquals("API key: ${Secret.MASK}", result)
        assertFalse(result.contains("sk-live-token"), result)
    }

    @Test
    fun `masks a secret in compact mode too`() {
        val values = mapOf<String, ConfigValue>("apiKey" to ConfigValue.SecretValue(Secret("sk-live-token")))

        val result = values.joinToString(definition("apiKey" to "API key"), compact = true)

        assertEquals(Secret.MASK, result)
    }

    @Test
    fun `omits an unset optional secret, so configured and not-configured stay distinguishable`() {
        val values = mapOf<String, ConfigValue>("endpoint" to ConfigValue.StringValue("https://x.io"))

        val result = values.joinToString(definition("apiKey" to "API key", "endpoint" to "Endpoint"))

        assertEquals("Endpoint: https://x.io", result)
        assertFalse(result.contains("API key"), result)
    }

    @Test
    fun `masks a per-task secret carried on a dataset item`() {
        val item = CustomDatasetItem(UUID.randomUUID(), mapOf("apiKey" to ConfigValue.SecretValue(Secret("sk-live-token"))))
        val values =
            mapOf<String, ConfigValue>(
                ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key to ConfigValue.CustomDatasetItemValue(item),
            )

        val result = values.joinToString(definition("apiKey" to "API key"))

        assertTrue(result.contains("API key: ${Secret.MASK}"), result)
        assertFalse(result.contains("sk-live-token"), result)
    }

    // endregion
}
