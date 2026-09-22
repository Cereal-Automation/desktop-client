package com.cereal.client.application.interactor.files

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AddCustomDatasetItemsFromFileToGroupInteractorTest {
    private val epoch = Instant.fromEpochSeconds(0)

    private val definition =
        listOf(
            ScriptConfigurationItemDefinition(
                name = "field",
                description = "description",
                key = "key",
                position = 0,
                type = ConfigItemType.StringConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
        )

    private fun group(id: String) = CustomDatasetGroup(id, "Group $id", 0, definition, emptySequence(), epoch)

    private fun item() = CustomDatasetItem(UUID.randomUUID(), mapOf("key" to ConfigValue.StringValue("value")))

    @Test
    fun `should add parsed items from the file to the group`() =
        runTest {
            val items = listOf(item(), item())
            val repository = InMemoryCustomDatasetRepository(fileItems = items)
            val target = group("group-1")
            repository.createDatasetGroup(target)
            val interactor = AddCustomDatasetItemsFromFileToGroupInteractor(repository)

            val result =
                interactor.run(
                    AddCustomDatasetItemsFromFileToGroupInteractor.Params(
                        file = File("/tmp/data.csv"),
                        group = target,
                    ),
                )

            assertEquals(2, result.group.numberOfItems)
            assertEquals(items, repository.getDatasetsFromGroup("group-1"))
        }

    @Test
    fun `should report zero items when the file is empty`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val target = group("group-1")
            repository.createDatasetGroup(target)
            val interactor = AddCustomDatasetItemsFromFileToGroupInteractor(repository)

            val result =
                interactor.run(
                    AddCustomDatasetItemsFromFileToGroupInteractor.Params(
                        file = File("/tmp/data.csv"),
                        group = target,
                    ),
                )

            assertEquals(0, result.group.numberOfItems)
            assertEquals(emptyList<CustomDatasetItem>(), repository.getDatasetsFromGroup("group-1"))
        }

    @Test
    fun `should throw and add nothing when reading the file fails`() =
        runTest {
            val repository =
                InMemoryCustomDatasetRepository(readError = RuntimeException("Parse error"))
            val target = group("group-1")
            repository.createDatasetGroup(target)
            val interactor = AddCustomDatasetItemsFromFileToGroupInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(
                    AddCustomDatasetItemsFromFileToGroupInteractor.Params(
                        file = File("/tmp/data.csv"),
                        group = target,
                    ),
                )
            }

            assertEquals(emptyList<CustomDatasetItem>(), repository.getDatasetsFromGroup("group-1"))
        }
}
