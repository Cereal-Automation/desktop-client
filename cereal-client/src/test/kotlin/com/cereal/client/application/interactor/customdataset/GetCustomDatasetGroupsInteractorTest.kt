package com.cereal.client.application.interactor.customdataset

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class GetCustomDatasetGroupsInteractorTest {
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

    @Test
    fun `should return custom dataset groups from repository`() =
        runTest {
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetCustomDatasetGroupsInteractor(customDatasetRepository)

            val groups =
                listOf(
                    CustomDatasetGroup("dataset-1", "Dataset One", 0, listOf(itemDefinition), emptySequence(), epoch),
                    CustomDatasetGroup("dataset-2", "Dataset Two", 0, listOf(itemDefinition), emptySequence(), epoch),
                )
            groups.forEach { customDatasetRepository.createDatasetGroup(it) }

            val result = interactor.run(Interactor.None()).first()

            assertEquals(groups, result)
        }

    @Test
    fun `should return empty list when no custom dataset groups exist`() =
        runTest {
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetCustomDatasetGroupsInteractor(customDatasetRepository)

            val result = interactor.run(Interactor.None()).first()

            assertEquals(emptyList<CustomDatasetGroup>(), result)
        }
}
