package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.datasets.DatasetFileInfo
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.provider.DatasetFileProvider
import java.io.File

/** No-op [DatasetFileProvider] for tests; [read] returns an empty file info. */
class InMemoryDatasetFileProvider : DatasetFileProvider {
    override fun saveTemplate(
        type: DatasetType,
        file: File,
    ) = Unit

    override fun read(
        type: DatasetType,
        file: File,
    ): DatasetFileInfo = DatasetFileInfo(numberOfRecords = 0)

    override fun readRows(file: File): List<Map<String, String>> = emptyList()
}
