package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.exception.InvalidScriptConfigurationDefinitionException
import com.cereal.sdk.FieldOption
import com.cereal.sdk.FieldOptions
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.ScriptConfigurationListItem
import com.cereal.sdk.statemodifier.ScriptConfig
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

class ScriptConfigurationDefinitionBuilderOptionsTest {
    private val builder = ScriptConfigurationDefinitionBuilder()

    @Suppress("UNCHECKED_CAST")
    private fun createFrom(clazz: KClass<*>) = builder.createFrom(clazz as KClass<ScriptConfiguration>)

    @Test
    fun `a String field carries its options object on the definition`() {
        val definition = createFrom(StringOptionsConfiguration::class)

        assertSame(AccountOptions, definition.configurationItems.first { it.key == "account" }.fieldOptions)
        assertNull(definition.configurationItems.first { it.key == "apiKey" }.fieldOptions)
    }

    @Test
    fun `options on a non-String field are rejected`() {
        assertThrows<InvalidScriptConfigurationDefinitionException> { createFrom(IntOptionsConfiguration::class) }
    }

    @Test
    fun `options on a list record field are rejected`() {
        assertThrows<InvalidScriptConfigurationDefinitionException> { createFrom(RecordOptionsConfiguration::class) }
    }

    @Test
    fun `options declared as a class rather than an object are rejected`() {
        assertThrows<InvalidScriptConfigurationDefinitionException> { createFrom(ClassOptionsConfiguration::class) }
    }

    object AccountOptions : FieldOptions {
        override val dependsOn = setOf("apiKey")

        override suspend fun load(config: ScriptConfig) = listOf(FieldOption("1"))
    }

    class ClassOptions : FieldOptions {
        override val dependsOn = emptySet<String>()

        override suspend fun load(config: ScriptConfig) = emptyList<FieldOption>()
    }

    interface StringOptionsConfiguration : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "apiKey", name = "API key", description = "")
        fun apiKey(): String

        @ScriptConfigurationItem(keyName = "account", name = "Account", description = "", options = AccountOptions::class)
        fun account(): String
    }

    interface IntOptionsConfiguration : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "count", name = "Count", description = "", options = AccountOptions::class)
        fun count(): Int
    }

    interface ClassOptionsConfiguration : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "account", name = "Account", description = "", options = ClassOptions::class)
        fun account(): String
    }

    interface Row : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "account", name = "Account", description = "", options = AccountOptions::class)
        fun account(): String
    }

    interface RecordOptionsConfiguration : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "rows", name = "Rows", description = "")
        fun rows(): List<Row>
    }
}
