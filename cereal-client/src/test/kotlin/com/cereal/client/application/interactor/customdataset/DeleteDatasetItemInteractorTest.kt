package com.cereal.client.application.interactor.customdataset

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DeleteDatasetItemInteractorTest {
    private val epoch = Instant.fromEpochSeconds(0)

    private val itemDefinition =
        ScriptConfigurationItemDefinition(
            name = "field",
            description = "description",
            key = "key",
            position = 0,
            type = ConfigItemType.StringConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private fun group(id: String) = CustomDatasetGroup(id, "Group $id", 0, listOf(itemDefinition), emptySequence(), epoch)

    private fun item() = CustomDatasetItem(UUID.randomUUID(), mapOf("key" to ConfigValue.StringValue("value")))

    @Test
    fun `should remove the item from its group`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val group = group("dataset-1")
            repository.createDatasetGroup(group)
            val keep = item()
            val remove = item()
            repository.createOrUpdateDataset(keep, group)
            repository.createOrUpdateDataset(remove, group)

            val interactor = DeleteDatasetItemInteractor(repository)

            interactor.run(DeleteDatasetItemInteractor.Params(remove))

            assertEquals(listOf(keep), repository.getDatasetsFromGroup("dataset-1"))
        }

    @Test
    fun `should leave group empty when deleting its only item`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val group = group("dataset-1")
            repository.createDatasetGroup(group)
            val only = item()
            repository.createOrUpdateDataset(only, group)

            val interactor = DeleteDatasetItemInteractor(repository)

            interactor.run(DeleteDatasetItemInteractor.Params(only))

            assertEquals(emptyList<CustomDatasetItem>(), repository.getDatasetsFromGroup("dataset-1"))
        }

    @Test
    fun `should propagate exception when repository delete fails`() =
        runTest {
            val repository = mockk<CustomDatasetRepository>()
            coEvery { repository.deleteDataset(any()) } throws RuntimeException("Database error")

            val interactor = DeleteDatasetItemInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(DeleteDatasetItemInteractor.Params(item()))
            }
        }
}
