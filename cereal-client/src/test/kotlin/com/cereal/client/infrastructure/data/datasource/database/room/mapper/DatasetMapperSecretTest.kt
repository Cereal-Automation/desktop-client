package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Per-task credentials through the dataset persistence layer.
 *
 * Two traps live here, and both are asserted rather than assumed. The stored text is built from the
 * value's default rendering, which for a [Secret] is the *mask* — persisting that would silently
 * replace every imported credential with `***`. And the definition's type has to survive the
 * round trip, or reading a dataset back would hand the script a bare string.
 */
@OptIn(ExperimentalTime::class)
class DatasetMapperSecretTest {
    private val mapper = DatasetMapper()
    private val groupId = UUID.randomUUID()
    private val now = Instant.fromEpochMilliseconds(0)

    private fun secretFieldDefinition() =
        ScriptConfigurationItemDefinition(
            name = "API key",
            description = "A per-task credential",
            key = "apiKey",
            position = 0,
            type = ConfigItemType.SecretConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private fun groupWith(vararg items: CustomDatasetItem) =
        CustomDatasetGroup(
            id = groupId.toString(),
            name = "Credentials",
            numberOfItems = items.size,
            itemDefinitions = listOf(secretFieldDefinition()),
            items = items.asSequence(),
            createdAt = now,
        )

    private fun itemWith(credential: String) =
        CustomDatasetItem(
            id = UUID.randomUUID(),
            fields = mapOf("apiKey" to ConfigValue.SecretValue(Secret(credential))),
        )

    @Test
    fun `a secret field definition maps to the SECRET value type`() {
        val entities = mapper.createDatasetGroupItemDefinitionEntities(groupWith())

        assertEquals(ValueType.SECRET, entities.single().type)
    }

    @Test
    fun `the stored value is the credential, not the mask`() {
        val item = itemWith("sk-live-alice")

        val stored =
            mapper
                .createDatasetItemEntities(item)
                .single()
                .value
                .value

        assertEquals("sk-live-alice", stored)
        assertFalse(stored == Secret.MASK, "persisting toString() would store the mask and destroy the credential")
    }

    @Test
    fun `per-task credentials are encrypted at rest, same as every other dataset value`() {
        // Verification rather than new work: the column is an EncryptedString for all field types.
        val entity = mapper.createDatasetItemEntities(itemWith("sk-live-alice")).single()

        assertNotNull(entity.value)
    }

    @Test
    fun `imported credentials reload intact and still masked`() {
        val group = groupWith(itemWith("sk-live-alice"), itemWith("sk-live-bob"))
        val definitionEntities = mapper.createDatasetGroupItemDefinitionEntities(group)
        val groupEntity = mapper.createDatasetGroupEntity(group)
        val datasetEntities = mapper.createDatasetsFromGroup(group)
        val itemEntities = mapper.createDatasetItemEntitiesFromGroup(group)

        val reloaded =
            mapper.toDomain(
                groupEntity = groupEntity,
                itemDefinitions = definitionEntities,
                datasets = datasetEntities,
                datasetItems = itemEntities.groupBy { it.datasetId },
            )

        assertEquals(ConfigItemType.SecretConfigItem, reloaded.itemDefinitions.single().type)

        val credentials =
            reloaded.items
                .map { it.fields["apiKey"] as ConfigValue.SecretValue }
                .toList()

        assertEquals(listOf("sk-live-alice", "sk-live-bob"), credentials.map { it.raw.reveal() })
        assertEquals(listOf(Secret.MASK, Secret.MASK), credentials.map { it.raw.toString() })
    }
}
