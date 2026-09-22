package com.cereal.client.application.interactor.customdataset

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class UpdateCustomDatasetGroupInteractorTest {
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

    private fun group(
        id: String,
        name: String,
    ) = CustomDatasetGroup(id, name, 0, listOf(itemDefinition), emptySequence(), epoch)

    @Test
    fun `should rename the group in the repository`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val original = group("dataset-1", "Old Name")
            repository.createDatasetGroup(original)

            val interactor = UpdateCustomDatasetGroupInteractor(repository)

            interactor.run(UpdateCustomDatasetGroupInteractor.Params(group = original, name = "New Name"))

            val stored = repository.getDatasetGroups().first()
            assertEquals(listOf("New Name"), stored.map { it.name })
            assertEquals(listOf("dataset-1"), stored.map { it.id })
        }

    @Test
    fun `should only rename the targeted group`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val one = group("dataset-1", "One")
            val two = group("dataset-2", "Two")
            repository.createDatasetGroup(one)
            repository.createDatasetGroup(two)

            val interactor = UpdateCustomDatasetGroupInteractor(repository)

            interactor.run(UpdateCustomDatasetGroupInteractor.Params(group = one, name = "Renamed"))

            val stored = repository.getDatasetGroups().first()
            assertEquals(mapOf("dataset-1" to "Renamed", "dataset-2" to "Two"), stored.associate { it.id to it.name })
        }

    @Test
    fun `should propagate exception when repository update fails`() =
        runTest {
            val repository = mockk<CustomDatasetRepository>()
            coEvery { repository.updateDatasetGroup(any()) } throws RuntimeException("Database error")

            val interactor = UpdateCustomDatasetGroupInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(
                    UpdateCustomDatasetGroupInteractor.Params(group = group("dataset-1", "Old"), name = "New"),
                )
            }
        }
}
