package com.cereal.client.application.interactor.files

import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.UUID

class ReadCustomDatasetFileInteractorTest {
    private val manifest =
        Manifest(
            packageName = "com.example.script",
            name = "Dataset Script",
            versionCode = 1L,
        )

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

    private fun item() = CustomDatasetItem(UUID.randomUUID(), mapOf("key" to ConfigValue.StringValue("value")))

    @Test
    fun `should create a dataset group with items parsed from the file`() =
        runTest {
            val items = listOf(item(), item())
            val repository = InMemoryCustomDatasetRepository(fileItems = items)
            val interactor = ReadCustomDatasetFileInteractor(repository)

            val result =
                interactor.run(
                    ReadCustomDatasetFileInteractor.Params(
                        file = File("/tmp/data.csv"),
                        manifest = manifest,
                        definition = definition,
                    ),
                )

            assertEquals(2, result.group.numberOfItems)
            assertEquals(definition, result.group.itemDefinitions)
            assertTrue(result.group.name.startsWith("Imported for Dataset Script at "))

            val storedGroups = repository.getDatasetGroups().first()
            assertEquals(listOf(result.group.id), storedGroups.map { it.id })
        }

    @Test
    fun `should create an empty dataset group when the file has no items`() =
        runTest {
            val repository = InMemoryCustomDatasetRepository()
            val interactor = ReadCustomDatasetFileInteractor(repository)

            val result =
                interactor.run(
                    ReadCustomDatasetFileInteractor.Params(
                        file = File("/tmp/data.csv"),
                        manifest = manifest,
                        definition = definition,
                    ),
                )

            assertEquals(0, result.group.numberOfItems)
            val storedGroups = repository.getDatasetGroups().first()
            assertEquals(listOf(result.group.id), storedGroups.map { it.id })
        }

    @Test
    fun `should throw and create no group when reading the file fails`() =
        runTest {
            val repository =
                InMemoryCustomDatasetRepository(readError = RuntimeException("Parse error"))
            val interactor = ReadCustomDatasetFileInteractor(repository)

            assertThrows<RuntimeException> {
                interactor.run(
                    ReadCustomDatasetFileInteractor.Params(
                        file = File("/tmp/data.csv"),
                        manifest = manifest,
                        definition = definition,
                    ),
                )
            }

            assertEquals(emptyList<Any>(), repository.getDatasetGroups().first())
        }
}
