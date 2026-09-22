package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.datasets.InvalidDatasetFileException

/**
 * How many problems a rejected file reports at once. Enough to fix a file in one pass without turning
 * the dialog into a wall of text. One more than this is collected, purely so the report can tell
 * "exactly this many" apart from "these and possibly more".
 */
private const val MAX_REPORTED_PROBLEMS = 10

/**
 * Reads raw CSV rows (header-keyed maps) into typed field values according to these
 * [ScriptConfigurationItemDefinition]s — the record's field definitions — returning one map per row.
 *
 * This is the single implementation of the CSV contract behind both importers:
 * [toListRows] for a list configuration item and
 * [com.cereal.client.domain.model.datasets.toDatasetItems] for a custom dataset. The two differ only in
 * what they build from the result, never in what they accept.
 *
 * The contract:
 * - A column matches a field by its declared key, compared trimmed and case-insensitively. Column
 *   order is irrelevant and columns the record does not declare are ignored.
 * - A column for a non-nullable field must be present. A column for a nullable field may be absent,
 *   in which case that field is `null` on every row.
 * - A blank cell means "no value"; on a non-nullable field that is an error.
 * - Conversion is [parseValue]'s, so booleans and enums are matched strictly.
 *
 * Import is all-or-nothing: any problem rejects the whole file. Up to [MAX_REPORTED_PROBLEMS] problems
 * are reported together so the file can be fixed in one pass, each naming its 1-based *data* row (the
 * header is not counted), its column, and the offending value.
 *
 * @throws InvalidDatasetFileException when the file violates any of these rules.
 */
internal fun List<ScriptConfigurationItemDefinition>.readCsvRecords(
    rows: List<Map<String, String>>,
): List<Map<ConfigKey, ConfigValue>> {
    if (rows.isEmpty()) {
        throw InvalidDatasetFileException("No rows found — the file contains only a header row.")
    }

    val headers = rows.first().keys
    requireUnambiguousColumns(headers)
    val columns = matchColumns(headers)
    requireEveryRequiredColumn(columns, headers)

    val problems = mutableListOf<String>()
    val records =
        rows.mapIndexed { index, row ->
            readRow(row, columns, rowNumber = index + 1, problems = problems)
        }
    if (problems.isNotEmpty()) {
        throw InvalidDatasetFileException(problems.toReport())
    }
    return records
}

private fun List<ScriptConfigurationItemDefinition>.requireEveryRequiredColumn(
    columns: Map<ConfigKey, String>,
    headers: Set<String>,
) {
    val missingColumns = filterNot { it.isNullable }.filter { columns[it.key] == null }
    if (missingColumns.isNotEmpty()) {
        throw InvalidDatasetFileException(
            "Missing required column(s): ${missingColumns.joinToString(", ") { "'${it.key}'" }}. " +
                "The file has: ${headers.joinToString(", ") { "'$it'" }}.",
        )
    }
}

/**
 * Reads one CSV row into the record's field values, appending a message per problem instead of
 * throwing, so a whole file's worth of problems is reported in one pass.
 */
private fun List<ScriptConfigurationItemDefinition>.readRow(
    row: Map<String, String>,
    columns: Map<ConfigKey, String>,
    rowNumber: Int,
    problems: MutableList<String>,
): Map<ConfigKey, ConfigValue> {
    val fields = mutableMapOf<ConfigKey, ConfigValue>()
    forEach { definition ->
        if (problems.size > MAX_REPORTED_PROBLEMS) return fields

        val rawValue = columns[definition.key]?.let { row[it] }?.takeIf { it.isNotBlank() }
        if (rawValue == null) {
            if (!definition.isNullable) {
                problems += "${definition.at(rowNumber)}: a value is required but the cell is empty."
            }
            return@forEach
        }

        try {
            definition.parseValue(rawValue)?.let { fields[definition.key] = it }
        } catch (_: IllegalArgumentException) {
            problems += "${definition.at(rowNumber)}: ${definition.type.describeRejection(rawValue)}"
        }
    }
    return fields
}

/**
 * Rejects a file whose headers match one of the record's fields more than once. Since matching ignores
 * case and surrounding space, `qty` and `QTY` are the same column — and picking one of them would be
 * exactly the silent wrong import this importer exists to avoid.
 */
private fun List<ScriptConfigurationItemDefinition>.requireUnambiguousColumns(headers: Set<String>) {
    val declared = map { it.key.trim().lowercase() }.toSet()
    val ambiguous =
        headers
            .groupBy { it.trim().lowercase() }
            .filter { (normalized, matches) -> normalized in declared && matches.size > 1 }
    if (ambiguous.isNotEmpty()) {
        throw InvalidDatasetFileException(
            "The file has more than one column for: " +
                ambiguous.values.joinToString("; ") { matches -> matches.joinToString(", ") { "'$it'" } } + ".",
        )
    }
}

/**
 * Matches the file's header names to the record's field keys, trimmed and case-insensitively, so a
 * spreadsheet export is not rejected over capitalisation or a stray space.
 */
private fun List<ScriptConfigurationItemDefinition>.matchColumns(headers: Set<String>): Map<ConfigKey, String> {
    val byNormalizedHeader = headers.associateBy { it.trim().lowercase() }
    return mapNotNull { definition ->
        byNormalizedHeader[definition.key.trim().lowercase()]?.let { definition.key to it }
    }.toMap()
}

private fun ScriptConfigurationItemDefinition.at(rowNumber: Int): String = "row $rowNumber, column '$key'"

private fun List<String>.toReport(): String {
    val truncated = size > MAX_REPORTED_PROBLEMS
    val heading =
        when {
            truncated -> "The first $MAX_REPORTED_PROBLEMS problems found (there may be more):"
            size == 1 -> "One problem was found:"
            else -> "$size problems were found:"
        }
    return (listOf(heading) + take(MAX_REPORTED_PROBLEMS)).joinToString("\n")
}
