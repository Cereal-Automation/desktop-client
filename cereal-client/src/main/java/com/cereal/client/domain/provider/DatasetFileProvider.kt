package com.cereal.client.domain.provider

import com.cereal.client.domain.model.datasets.DatasetFileInfo
import com.cereal.client.domain.model.datasets.DatasetType
import java.io.File

interface DatasetFileProvider {
    fun saveTemplate(
        type: DatasetType,
        file: File,
    )

    fun read(
        type: DatasetType,
        file: File,
    ): DatasetFileInfo

    /**
     * Reads a CSV file as raw header-keyed rows, without interpreting any of them. The caller pairs
     * these with the field definitions that give them meaning.
     */
    fun readRows(file: File): List<Map<String, String>>
}
