package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import com.cereal.sdk.ScriptConfiguration
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The configuration round trip for credentials: a secret written out and read back must come back as a
 * [Secret], not as a bare string that has quietly lost its masking.
 */
@OptIn(ExperimentalTime::class)
class KeyValueRoomMapperSecretTest {
    private val mapper =
        KeyValueRoomMapper(
            proxyDataSource = mockk(relaxed = true),
            datasetDataSource = null,
        )
    private val user = mockk<User>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(0)

    private fun configurationItemEntity(
        value: String?,
        type: ValueType,
    ) = ScriptConfigurationItemEntity(
        id = UUID.randomUUID(),
        configurationId = UUID.randomUUID(),
        key = "apiKey",
        value = value?.let { EncryptedString(it) },
        type = type,
        createdAt = now,
        updatedAt = now,
    )

    /** A definition declaring the `apiKey` item as [type] — the current shape of the script's contract. */
    private fun definitionDeclaring(type: ConfigItemType) =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems =
                listOf(
                    ScriptConfigurationItemDefinition(
                        name = "API key",
                        description = "A credential",
                        key = "apiKey",
                        position = 0,
                        type = type,
                        isNullable = false,
                        stateModifier = null,
                        isScriptIdentifier = false,
                    ),
                ),
        )

    @Test
    fun `getValueType labels a Secret as SECRET rather than STRING`() {
        assertEquals(ValueType.SECRET, mapper.getValueType(Secret("sk-live-token")))
    }

    @Test
    fun `valueToString stores the plaintext, not the mask`() {
        // The value column is an EncryptedString regardless of type, so the plaintext is what gets
        // encrypted. Storing `toString()` here would persist "***" and destroy the credential.
        assertEquals("sk-live-token", mapper.valueToString(Secret("sk-live-token")))
    }

    @Test
    fun `a secret configuration value round-trips back into a SecretValue`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.SECRET)),
                    user = user,
                    scriptConfigurationDefinition = null,
                )

            assertEquals(ConfigValue.SecretValue(Secret("sk-live-token")), result["apiKey"])
        }

    @Test
    fun `a round-tripped secret still masks on toString`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.SECRET)),
                    user = user,
                    scriptConfigurationDefinition = null,
                )

            assertEquals(Secret.MASK, result["apiKey"]?.raw?.toString())
        }

    @Test
    fun `a round-tripped secret reveals the original plaintext`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.SECRET)),
                    user = user,
                    scriptConfigurationDefinition = null,
                )

            assertEquals("sk-live-token", (result["apiKey"] as ConfigValue.SecretValue).raw.reveal())
        }

    // region String -> Secret migration (ticket 06)

    @Test
    fun `a value stored as text is coerced to a secret when the definition now declares one`() =
        runTest {
            // The adoption path: a shipped script changes an item from String to Secret, keeping the
            // key name. Without coercion, retrieval dispatches on the stored type, mismatches the
            // declared type, and degrades to no value — blanking every existing user's credential.
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.STRING)),
                    user = user,
                    scriptConfigurationDefinition = definitionDeclaring(ConfigItemType.SecretConfigItem),
                )

            assertEquals(ConfigValue.SecretValue(Secret("sk-live-token")), result["apiKey"])
        }

    @Test
    fun `a migrated credential survives intact and reveals its original value`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.STRING)),
                    user = user,
                    scriptConfigurationDefinition = definitionDeclaring(ConfigItemType.SecretConfigItem),
                )

            assertEquals("sk-live-token", (result["apiKey"] as ConfigValue.SecretValue).raw.reveal())
        }

    @Test
    fun `a coerced value is labelled SECRET, so the next save re-persists it under the secret type`() =
        runTest {
            val coerced =
                mapper
                    .mapConfigurationItemsFromEntities(
                        configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.STRING)),
                        user = user,
                        scriptConfigurationDefinition = definitionDeclaring(ConfigItemType.SecretConfigItem),
                    )["apiKey"]!!
                    .raw

            assertEquals(ValueType.SECRET, mapper.getValueType(coerced))
            assertEquals("sk-live-token", mapper.valueToString(coerced))
        }

    @Test
    fun `a text value stays a string when the definition still declares text`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("plain-value", ValueType.STRING)),
                    user = user,
                    scriptConfigurationDefinition = definitionDeclaring(ConfigItemType.StringConfigItem),
                )

            assertEquals(ConfigValue.StringValue("plain-value"), result["apiKey"])
        }

    @Test
    fun `a text value stays a string when there is no definition to consult`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("plain-value", ValueType.STRING)),
                    user = user,
                    scriptConfigurationDefinition = null,
                )

            assertEquals(ConfigValue.StringValue("plain-value"), result["apiKey"])
        }

    @Test
    fun `a stored secret is not coerced down to a string for a text definition`() =
        runTest {
            // The coercion is one-directional on purpose: a downgrade would take a value the user was
            // told is protected and start printing it in the task list.
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity("sk-live-token", ValueType.SECRET)),
                    user = user,
                    scriptConfigurationDefinition = definitionDeclaring(ConfigItemType.StringConfigItem),
                )

            assertEquals(ConfigValue.SecretValue(Secret("sk-live-token")), result["apiKey"])
            assertTrue(result["apiKey"]?.raw is Secret, "a stored secret must never be un-masked on read")
        }

    @Test
    fun `an absent stored value stays absent rather than becoming an empty secret`() =
        runTest {
            val result =
                mapper.mapConfigurationItemsFromEntities(
                    configurationItems = listOf(configurationItemEntity(null, ValueType.STRING)),
                    user = user,
                    scriptConfigurationDefinition = definitionDeclaring(ConfigItemType.SecretConfigItem),
                )

            assertNull(result["apiKey"])
        }

    // endregion

    @Test
    fun `a task configuration secret round-trips too, so a task snapshot keeps its masking`() =
        runTest {
            val entity =
                TaskConfigurationEntity(
                    id = UUID.randomUUID(),
                    taskId = UUID.randomUUID(),
                    key = "apiKey",
                    value = EncryptedString("sk-live-token"),
                    type = ValueType.SECRET,
                    createdAt = now,
                    updatedAt = now,
                )

            val result =
                mapper.mapTaskConfigurationFromEntities(
                    configurationEntities = listOf(entity),
                    user = user,
                    scriptConfigurationDefinition = null,
                )

            assertEquals(ConfigValue.SecretValue(Secret("sk-live-token")), result["apiKey"])
        }
}
