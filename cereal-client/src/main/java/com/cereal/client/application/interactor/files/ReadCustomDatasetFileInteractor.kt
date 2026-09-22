package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.repository.CustomDatasetRepository
import java.io.File
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ReadCustomDatasetFileInteractor(
    private val customDatasetRepository: CustomDatasetRepository,
) : Interactor<ReadCustomDatasetFileInteractor.Result, ReadCustomDatasetFileInteractor.Params>() {
    override suspend fun run(params: Params): Result {
        val customDatasets =
            customDatasetRepository.readFromFile(
                params.file,
                params.definition,
            )
        val group =
            CustomDatasetGroup(
                id = UUID.randomUUID().toString(),
                itemDefinitions = params.definition,
                name = createGroupName(params.manifest),
                items = customDatasets.asSequence(),
                numberOfItems = customDatasets.size,
                createdAt = Clock.System.now(),
            )

        customDatasetRepository.createDatasetGroup(group)

        return Result(group)
    }

    data class Params(
        val file: File,
        val manifest: Manifest,
        val definition: List<ScriptConfigurationItemDefinition>,
    )

    data class Result(
        val group: CustomDatasetGroup,
    )
}
