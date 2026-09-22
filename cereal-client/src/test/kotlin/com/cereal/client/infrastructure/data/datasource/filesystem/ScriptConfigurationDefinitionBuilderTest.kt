package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.exception.InvalidScriptConfigurationDefinitionException
import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.models.Secret
import com.cereal.sdk.statemodifier.DefaultStateModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class ScriptConfigurationDefinitionBuilderTest {
    private val builder = ScriptConfigurationDefinitionBuilder()

    @Test
    fun `createFrom extracts string default value`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(StringDefaultConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "stringKey" }
        assertEquals(ConfigValue.StringValue("default string value"), item.defaultValue)
    }

    @Test
    fun `createFrom extracts int default value`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(IntDefaultConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "intKey" }
        assertEquals(ConfigValue.IntValue(42), item.defaultValue)
    }

    @Test
    fun `createFrom extracts boolean default value`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(BooleanDefaultConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "boolKey" }
        assertEquals(ConfigValue.BooleanValue(true), item.defaultValue)
    }

    @Test
    fun `createFrom extracts float default value`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(FloatDefaultConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "floatKey" }
        assertEquals(ConfigValue.FloatValue(3.14f), item.defaultValue)
    }

    @Test
    fun `createFrom extracts double default value`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(DoubleDefaultConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "doubleKey" }
        assertEquals(ConfigValue.DoubleValue(2.718), item.defaultValue)
    }

    @Test
    fun `createFrom extracts enum default value`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(EnumDefaultConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "enumKey" }
        assertEquals(ConfigValue.EnumValue(TestEnum.SECOND), item.defaultValue)
    }

    @Test
    fun `createFrom returns null for nullable without default`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(NullableConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "nullableKey" }
        assertNull(item.defaultValue)
    }

    @Test
    fun `createFrom handles multiple configuration items with defaults`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(MixedConfiguration::class as KClass<ScriptConfiguration>)

        val stringItem = definition.configurationItems.first { it.key == "stringKey" }
        val intItem = definition.configurationItems.first { it.key == "intKey" }
        val nullableItem = definition.configurationItems.first { it.key == "nullableKey" }

        assertEquals(ConfigValue.StringValue("hello"), stringItem.defaultValue)
        assertEquals(ConfigValue.IntValue(100), intItem.defaultValue)
        assertNull(nullableItem.defaultValue)
    }

    // region Secret

    @Test
    fun `createFrom maps a Secret return type to the secret item type`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(SecretConfiguration::class as KClass<ScriptConfiguration>)

        val item = definition.configurationItems.first { it.key == "apiKey" }
        assertEquals(ConfigItemType.SecretConfigItem, item.type)
    }

    @Test
    fun `createFrom accepts a Secret alongside other supported return types`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(SecretAndStringConfiguration::class as KClass<ScriptConfiguration>)

        assertEquals(
            ConfigItemType.SecretConfigItem,
            definition.configurationItems.first { it.key == "apiKey" }.type,
        )
        assertEquals(
            ConfigItemType.StringConfigItem,
            definition.configurationItems.first { it.key == "endpoint" }.type,
        )
    }

    @Test
    fun `createFrom accepts a per-task secret and surfaces it as task data`() {
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(PerTaskSecretConfiguration::class as KClass<ScriptConfiguration>)

        // Per-task items are collected into the synthetic "Task data" grouped item rather than
        // appearing as top-level configuration items.
        val taskData =
            definition.configurationItems.first {
                it.key == ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key
            }
        val fields = (taskData.type as ConfigItemType.GroupedConfigItem).items

        assertEquals(ConfigItemType.SecretConfigItem, fields.first { it.key == "apiKey" }.type)
    }

    @Test
    fun `createFrom rejects a secret declared as the script identifier`() {
        val error =
            assertThrows(InvalidScriptConfigurationDefinitionException::class.java) {
                @Suppress("UNCHECKED_CAST")
                builder.createFrom(SecretScriptIdentifierConfiguration::class as KClass<ScriptConfiguration>)
            }

        // The error must name the reason, not just fail — the developer has to know why.
        assertTrue(error.message!!.contains("Secret"), error.message)
        assertTrue(error.message!!.contains("script identifier"), error.message)
    }

    @Test
    fun `createFrom rejects a per-task secret declared as the script identifier`() {
        // Per-task items are nested inside the synthetic "Task data" grouped item rather than
        // appearing at top level, so a check that only scans the top-level list misses them.
        val error =
            assertThrows(InvalidScriptConfigurationDefinitionException::class.java) {
                @Suppress("UNCHECKED_CAST")
                builder.createFrom(PerTaskSecretScriptIdentifierConfiguration::class as KClass<ScriptConfiguration>)
            }

        assertTrue(error.message!!.contains("Secret"), error.message)
        assertTrue(error.message!!.contains("script identifier"), error.message)
    }

    @Test
    fun `createFrom rejects a list of secrets as an unsupported list type`() {
        val error =
            assertThrows(UnsupportedConfigurationTypeException::class.java) {
                @Suppress("UNCHECKED_CAST")
                builder.createFrom(SecretListConfiguration::class as KClass<ScriptConfiguration>)
            }

        // Asserts the offending element type is named, not the surrounding prose: the list of
        // *supported* element types is master's to grow (it gained lists), and coupling to
        // that sentence makes this test fail for reasons that have nothing to do with secrets.
        assertTrue(error.message!!.contains("Secret"), error.message)
        assertTrue(error.message!!.contains("apiKeys"), error.message)
    }

    @Test
    fun `createFrom does not extract a default value for a secret`() {
        // Asserted rather than left to fall out of the default-value type filter: a default returning
        // a credential would be a hardcoded secret in source.
        @Suppress("UNCHECKED_CAST")
        val definition = builder.createFrom(SecretWithDefaultConfiguration::class as KClass<ScriptConfiguration>)

        assertNull(definition.configurationItems.first { it.key == "apiKey" }.defaultValue)
    }

    // endregion
}

// Test configuration interfaces

interface StringDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "stringKey",
        name = "String Key",
        description = "A string configuration with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun stringValue(): String = "default string value"
}

interface IntDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "intKey",
        name = "Int Key",
        description = "An integer configuration with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun intValue(): Int = 42
}

interface BooleanDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "boolKey",
        name = "Boolean Key",
        description = "A boolean configuration with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun boolValue(): Boolean = true
}

interface FloatDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "floatKey",
        name = "Float Key",
        description = "A float configuration with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun floatValue(): Float = 3.14f
}

interface DoubleDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "doubleKey",
        name = "Double Key",
        description = "A double configuration with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun doubleValue(): Double = 2.718
}

enum class TestEnum {
    FIRST,
    SECOND,
    THIRD,
}

interface EnumDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "enumKey",
        name = "Enum Key",
        description = "An enum configuration with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun enumValue(): TestEnum = TestEnum.SECOND
}

interface NullableConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "nullableKey",
        name = "Nullable Key",
        description = "A nullable configuration without explicit default",
        stateModifier = DefaultStateModifier::class,
    )
    fun nullableValue(): String?
}

interface SecretConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKey",
        name = "API key",
        description = "A credential",
        stateModifier = DefaultStateModifier::class,
    )
    fun apiKey(): Secret
}

interface SecretAndStringConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKey",
        name = "API key",
        description = "A credential",
        stateModifier = DefaultStateModifier::class,
    )
    fun apiKey(): Secret

    @ScriptConfigurationItem(
        keyName = "endpoint",
        name = "Endpoint",
        description = "An ordinary text item",
        stateModifier = DefaultStateModifier::class,
    )
    fun endpoint(): String
}

interface PerTaskSecretConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKey",
        name = "API key",
        description = "A credential supplied per task",
        stateModifier = DefaultStateModifier::class,
        valuePerTask = true,
    )
    fun apiKey(): Secret
}

interface SecretScriptIdentifierConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKey",
        name = "API key",
        description = "A credential used, illegally, as the identifier",
        stateModifier = DefaultStateModifier::class,
        isScriptIdentifier = true,
    )
    fun apiKey(): Secret
}

interface PerTaskSecretScriptIdentifierConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKey",
        name = "API key",
        description = "A per-task credential used, illegally, as the identifier",
        stateModifier = DefaultStateModifier::class,
        isScriptIdentifier = true,
        valuePerTask = true,
    )
    fun apiKey(): Secret
}

interface SecretListConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKeys",
        name = "API keys",
        description = "A list of credentials, which is unsupported",
        stateModifier = DefaultStateModifier::class,
    )
    fun apiKeys(): List<Secret>
}

interface SecretWithDefaultConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "apiKey",
        name = "API key",
        description = "A credential with a default the platform must ignore",
        stateModifier = DefaultStateModifier::class,
    )
    fun apiKey(): Secret = Secret("hardcoded-in-source")
}

interface MixedConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "stringKey",
        name = "String Key",
        description = "String with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun stringValue(): String = "hello"

    @ScriptConfigurationItem(
        keyName = "intKey",
        name = "Int Key",
        description = "Int with default",
        stateModifier = DefaultStateModifier::class,
    )
    fun intValue(): Int = 100

    @ScriptConfigurationItem(
        keyName = "nullableKey",
        name = "Nullable Key",
        description = "Nullable without default",
        stateModifier = DefaultStateModifier::class,
    )
    fun nullableValue(): String?
}
