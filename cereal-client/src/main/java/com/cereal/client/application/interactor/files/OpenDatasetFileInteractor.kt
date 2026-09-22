package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.DatasetFileInfo
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.provider.DatasetFileProvider
import java.io.File

class OpenDatasetFileInteractor(
    private val datasetFileRepository: DatasetFileProvider,
) : Interactor<OpenDatasetFileInteractor.Result, OpenDatasetFileInteractor.Params>() {
    override suspend fun run(params: Params): Result {
        val fileInfo = datasetFileRepository.read(params.type, params.file)

        return Result(fileInfo)
    }

    data class Params(
        val type: DatasetType,
        val file: File,
    )

    data class Result(
        val fileInfo: DatasetFileInfo,
    )
}
