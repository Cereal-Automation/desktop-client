package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.application.datasets.toInvalidFileException
import com.cereal.client.domain.model.datasets.InvalidDatasetFileException
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.toListRows
import com.cereal.client.domain.provider.DatasetFileProvider
import java.io.File

/**
 * Reads a CSV file into the rows of a list configuration item, pairing the provider's raw
 * read with the record's field definitions.
 *
 * The file is not remembered: only the rows it produced reach the form, so moving or deleting it
 * afterwards has no effect on the configuration.
 */
class ReadListFileInteractor(
    private val datasetFileProvider: DatasetFileProvider,
) : Interactor<ReadListFileInteractor.Result, ReadListFileInteractor.Params>() {
    override suspend fun run(params: Params): Result =
        try {
            Result(params.definitions.toListRows(datasetFileProvider.readRows(params.file)))
        } catch (e: InvalidDatasetFileException) {
            throw e.toInvalidFileException()
        }

    data class Params(
        val file: File,
        val definitions: List<ScriptConfigurationItemDefinition>,
    )

    data class Result(
        val rows: ListRows,
    )
}
