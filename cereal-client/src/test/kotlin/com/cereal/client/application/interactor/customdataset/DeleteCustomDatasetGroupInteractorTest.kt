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
class DeleteCustomDatasetGroupInteractorTest {
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

    @Test
    fun `should remove the group from the repository`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val target = group("dataset-1")
            val other = group("dataset-2")
            repository.createDatasetGroup(target)
            repository.createDatasetGroup(other)

            val interactor = DeleteCustomDatasetGroupInteractor(repository)

            interactor.run(DeleteCustomDatasetGroupInteractor.Params(target))

            val stored = repository.getDatasetGroups().first()
            assertEquals(listOf("dataset-2"), stored.map { it.id })
        }

    @Test
    fun `should leave repository empty when deleting the only group`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val target = group("dataset-1")
            repository.createDatasetGroup(target)

            val interactor = DeleteCustomDatasetGroupInteractor(repository)

            interactor.run(DeleteCustomDatasetGroupInteractor.Params(target))

            assertEquals(emptyList<CustomDatasetGroup>(), repository.getDatasetGroups().first())
        }

    @Test
    fun `should propagate exception when repository delete fails`() =
        runTest {
            val repository = mockk<CustomDatasetRepository>()
            coEvery { repository.deleteDatasetGroup(any()) } throws RuntimeException("Database error")

            val interactor = DeleteCustomDatasetGroupInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(DeleteCustomDatasetGroupInteractor.Params(group("dataset-1")))
            }
        }
}
