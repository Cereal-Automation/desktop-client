package com.cereal.client.domain.model.script.configuration

/**
 * The rows a user entered for a list configuration item — an item returning
 * `List<T : ScriptConfigurationListItem>`, where each row is a record of typed fields.
 *
 * Wrapping the rows instead of using a bare `List<ListRow>` is deliberate: the wrapper is where the
 * rows' [describe] rendering and the [EMPTY] instance live, and it keeps the wrapped value a single
 * distinguishable type at the boundaries that discriminate on the *runtime type* of [ConfigValue.raw]
 * (see `Any.toConfigValue`, `KeyValueRoomMapper.getValueType`) — a bare `List` reaching those
 * boundaries is an unsupported value and is rejected rather than coerced.
 */
data class ListRows(
    val rows: List<ListRow>,
) {
    /** A one-line human-readable rendering of every row, for display and diagnostics. */
    fun describe(): String = rows.joinToString("; ") { it.describe() }

    companion object {
        val EMPTY = ListRows(emptyList())
    }
}

/**
 * One row of a list: the values the user entered for the record's fields, keyed by the field's
 * own [ConfigKey]. A field the user left blank is absent rather than mapped to a placeholder, so
 * "unset" survives a round trip to storage and reaches the script as `null`.
 */
data class ListRow(
    val fields: Map<ConfigKey, ConfigValue>,
) {
    /** A one-line human-readable rendering of this row's values, for display and diagnostics. */
    fun describe(): String = fields.entries.joinToString(", ") { "${it.key}=${it.value.raw}" }
}
