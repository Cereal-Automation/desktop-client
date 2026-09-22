package com.cereal.client.domain.model.datasets

import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.readCsvRecords
import java.util.UUID

/**
 * Builds validated [CustomDatasetItem]s from raw CSV rows (header-keyed maps) according to these
 * [ScriptConfigurationItemDefinition]s.
 *
 * The CSV contract is [readCsvRecords]'s, shared with the list importer: columns match a field
 * by its declared key regardless of case or surrounding space, a column for a nullable field may be
 * absent, blank cells mean "no value", and every problem in the file is reported at once.
 *
 * Every declared field appears on every item, holding `null` where the row supplied no value, so an
 * item's shape always mirrors the definitions it was read against.
 *
 * @throws InvalidDatasetFileException when the file violates the contract.
 */
fun List<ScriptConfigurationItemDefinition>.toDatasetItems(rows: List<Map<String, String>>): List<CustomDatasetItem> {
    val definitions = this
    return readCsvRecords(rows).map { record ->
        CustomDatasetItem(
            id = UUID.randomUUID(),
            fields = definitions.associate { it.key to record[it.key] },
        )
    }
}
