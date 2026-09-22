package com.cereal.client.infrastructure.provider

import com.cereal.client.application.datasets.toInvalidFileException
import com.cereal.client.domain.model.datasets.DatasetFileInfo
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.datasets.InvalidDatasetFileException
import com.cereal.client.domain.model.datasets.toDatasetItems
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.toListRows
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import com.cereal.client.infrastructure.data.datasource.csv.CsvWriter
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import java.io.File

class DatasetFileProviderImpl(
    private val csvReader: CsvReader,
    private val csvWriter: CsvWriter,
    private val fileSystemProxyTemplateDataSource: FileSystemProxyTemplateDataSource,
) : DatasetFileProvider {
    override fun saveTemplate(
        type: DatasetType,
        file: File,
    ) {
        when (type) {
            is DatasetType.Custom -> {
                writeCsvTemplate(type.definitions, file)
            }

            is DatasetType.ConfigList -> {
                writeCsvTemplate(type.definitions, file)
            }

            DatasetType.Proxy -> {
                fileSystemProxyTemplateDataSource.writeTemplate(file.ensureTxtExtension())
            }
        }
    }

    override fun read(
        type: DatasetType,
        file: File,
    ): DatasetFileInfo =
        when (type) {
            is DatasetType.Custom -> {
                DatasetFileInfo(countValidatedRows(file) { type.definitions.toDatasetItems(it).size })
            }

            is DatasetType.ConfigList -> {
                DatasetFileInfo(
                    countValidatedRows(file) {
                        type.definitions
                            .toListRows(it)
                            .rows.size
                    },
                )
            }

            DatasetType.Proxy -> {
                readProxyFile(file)
            }
        }

    override fun readRows(file: File): List<Map<String, String>> = csvReader.readRawRows(file)

    private fun writeCsvTemplate(
        definitions: List<ScriptConfigurationItemDefinition>,
        file: File,
    ) {
        val headers = definitions.sortedBy { it.position }.map { it.key }
        csvWriter.write(file.ensureCsvExtension(), listOf(headers))
    }

    /**
     * Parses and validates the whole file with [countRows], the same mapping the import itself performs,
     * so the dialog can list everything that is wrong before the user commits to importing. The rows
     * themselves are read again on import; only the count is needed here.
     */
    private fun countValidatedRows(
        file: File,
        countRows: (List<Map<String, String>>) -> Int,
    ): Int =
        try {
            countRows(csvReader.readRawRows(file))
        } catch (e: InvalidDatasetFileException) {
            throw e.toInvalidFileException()
        }

    private fun readProxyFile(file: File): DatasetFileInfo {
        val records = fileSystemProxyTemplateDataSource.readFromTemplate(file)
        return DatasetFileInfo(records.size)
    }
}

private fun File.ensureCsvExtension(): File = ensureExtension(".csv")

private fun File.ensureTxtExtension(): File = ensureExtension(".txt")

private fun File.ensureExtension(extension: String): File =
    if (this.name.endsWith(extension)) {
        this
    } else {
        File(this.parentFile, "${this.name}$extension")
    }
