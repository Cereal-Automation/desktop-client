package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.provider.DatasetFileProvider
import java.io.File

class DownloadDatasetTemplateFileInteractor(
    private val datasetFileRepository: DatasetFileProvider,
) : Interactor<Unit, DownloadDatasetTemplateFileInteractor.Params>() {
    override suspend fun run(params: Params) {
        datasetFileRepository.saveTemplate(params.datasetType, params.file)
    }

    data class Params(
        val datasetType: DatasetType,
        val file: File,
    )
}
