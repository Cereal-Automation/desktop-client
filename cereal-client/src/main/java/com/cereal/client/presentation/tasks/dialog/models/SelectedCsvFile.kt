package com.cereal.client.presentation.tasks.dialog.models

import com.cereal.client.domain.model.datasets.DatasetFileInfo
import java.io.File

data class SelectedCsvFile(
    val file: File,
    val fileInfo: DatasetFileInfo,
)
