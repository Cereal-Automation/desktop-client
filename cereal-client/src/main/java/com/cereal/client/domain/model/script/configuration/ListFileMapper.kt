package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.datasets.InvalidDatasetFileException

/**
 * The most rows a single list import may contain. A product decision rather than a technical
 * ceiling: past this the configuration value approaches a megabyte of JSON that is encrypted and
 * decrypted as one database column on every save and load.
 */
const val MAX_LIST_IMPORT_ROWS = 5000

/**
 * Builds the rows of a list configuration item from raw CSV rows (header-keyed maps)
 * according to these [ScriptConfigurationItemDefinition]s — the record's field definitions.
 *
 * The CSV contract is [readCsvRecords]'s, shared with the custom-dataset importer. On top of it this
 * importer enforces [MAX_LIST_IMPORT_ROWS], the ceiling a configuration value has and a dataset does
 * not.
 *
 * @throws InvalidDatasetFileException when the file violates the contract or exceeds the ceiling.
 */
fun List<ScriptConfigurationItemDefinition>.toListRows(rows: List<Map<String, String>>): ListRows {
    if (rows.size > MAX_LIST_IMPORT_ROWS) {
        throw InvalidDatasetFileException(
            "The file contains ${rows.size} rows, which is more than the maximum of $MAX_LIST_IMPORT_ROWS rows " +
                "that can be imported at once.",
        )
    }
    return ListRows(readCsvRecords(rows).map(::ListRow))
}
