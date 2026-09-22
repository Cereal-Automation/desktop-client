package com.cereal.client.presentation.tasks.dialog

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.application.interactor.files.DownloadDatasetTemplateFileInteractor
import com.cereal.client.application.interactor.files.OpenDatasetFileInteractor
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.dialog.models.SelectedCsvFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImportFromFileViewModel(
    private val scope: CoroutineScope,
    private val datasetType: DatasetType,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val errorResolver: ErrorResolver,
    private val openDatasetFileInteractor: OpenDatasetFileInteractor,
    private val downloadDatasetFileInteractor: DownloadDatasetTemplateFileInteractor,
) {
    val selectedFile = mutableStateOf<SelectedCsvFile?>(null)
    val fileErrorMessage = mutableStateOf<String?>(null)
    val fileTypeName = mutableStateOf<String?>(null)

    init {
        when (datasetType) {
            is DatasetType.Custom, is DatasetType.ConfigList -> fileTypeName.value = "CSV"
            DatasetType.Proxy -> fileTypeName.value = "proxy list"
        }
    }

    fun onOpenFile(file: File) {
        scope.launch(dispatcherProvider.io) {
            openDatasetFileInteractor(
                OpenDatasetFileInteractor.Params(datasetType, file),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    result.fold({ result ->
                        selectedFile.value = SelectedCsvFile(file, result.fileInfo)
                    }, { error ->
                        fileErrorMessage.value =
                            when (error) {
                                is InvalidFileException -> {
                                    error.message ?: "File contains invalid data."
                                }

                                else -> {
                                    error.message ?: "We couldn't read that file. Please check it and try again."
                                }
                            }
                    })
                }
            }
        }
    }

    fun onDownloadTemplate(file: File) {
        scope.launch(dispatcherProvider.io) {
            downloadDatasetFileInteractor(DownloadDatasetTemplateFileInteractor.Params(datasetType, file)) { result ->
                result.handleFailureOrElse(errorResolver) {
                    // No-op
                }
            }
        }
    }
}
