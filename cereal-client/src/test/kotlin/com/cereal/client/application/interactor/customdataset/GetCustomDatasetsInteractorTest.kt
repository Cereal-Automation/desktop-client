package com.cereal.client.application.interactor.customdataset

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class GetCustomDatasetsInteractorTest {
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
    fun `should return datasets for the requested group`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val group = group("dataset-1")
            repository.createDatasetGroup(group)
            val first = item()
            val second = item()
            repository.createOrUpdateDataset(first, group)
            repository.createOrUpdateDataset(second, group)

            val interactor = GetCustomDatasetsInteractor(repository)

            val result = interactor.run(GetCustomDatasetsInteractor.Params(group)).first()

            assertEquals(listOf(first, second), result)
        }

    @Test
    fun `should return empty list when group has no datasets`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val group = group("dataset-empty")
            repository.createDatasetGroup(group)

            val interactor = GetCustomDatasetsInteractor(repository)

            val result = interactor.run(GetCustomDatasetsInteractor.Params(group)).first()

            assertEquals(emptyList<CustomDatasetItem>(), result)
        }

    @Test
    fun `should return only datasets belonging to the requested group`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val groupOne = group("dataset-1")
            val groupTwo = group("dataset-2")
            repository.createDatasetGroup(groupOne)
            repository.createDatasetGroup(groupTwo)
            val itemOne = item()
            val itemTwo = item()
            repository.createOrUpdateDataset(itemOne, groupOne)
            repository.createOrUpdateDataset(itemTwo, groupTwo)

            val interactor = GetCustomDatasetsInteractor(repository)

            val result = interactor.run(GetCustomDatasetsInteractor.Params(groupOne)).first()

            assertEquals(listOf(itemOne), result)
        }
}
