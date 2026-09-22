package com.cereal.client.presentation.view.fields.state

import com.cereal.client.domain.model.script.configuration.ConfigKey
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.toConfigValue

/**
 * One row of an [ListFieldState]: the form field states for a single record, in the order the
 * record declares its fields.
 *
 * Field states are added after construction ([putFieldState]) so that each one can be given a
 * [com.cereal.sdk.statemodifier.ScriptConfig] view of *this* row — which needs the row to exist first.
 *
 * @param onRowValueChange notified when one of this row's fields changes, via [onFieldValueChanged].
 */
class ListRowState(
    val fieldDefinitions: List<ScriptConfigurationItemDefinition>,
    private val onRowValueChange: () -> Unit = {},
) {
    private val states = LinkedHashMap<ConfigKey, FormFieldState<*, *>>()

    /**
     * This row's values, recomputed only after one of its own fields changes.
     *
     * The whole configuration's values are collected on every keystroke — twice, once for visibility
     * and once for required-ness — and each collection walks every row. Without this cache, typing one
     * character in a list of two thousand rows re-parses every cell of every row.
     */
    private var cachedFields: Map<ConfigKey, ConfigValue>? = null

    val fieldStates: Map<ConfigKey, FormFieldState<*, *>> get() = states

    fun putFieldState(
        key: ConfigKey,
        state: FormFieldState<*, *>,
    ) {
        states[key] = state
        cachedFields = null
    }

    /**
     * Called by this row's field states when their value changes. Drops the cached values and tells
     * the list — one hook, so a value can never change without the cache being dropped.
     */
    fun onFieldValueChanged() {
        cachedFields = null
        onRowValueChange()
    }

    /**
     * The row's values, keyed by field key, in the order the record declares its fields.
     *
     * A nullable field the user left blank is omitted rather than mapped to a placeholder, so it arrives
     * at the script as `null` — including a nullable boolean whose switch was never touched, which would
     * otherwise always arrive as `false`. A non-nullable field keeps whatever value its widget holds, and
     * a missing one is reported as a validation error instead of being silently defaulted.
     */
    fun fields(): Map<ConfigKey, ConfigValue> = cachedFields ?: computeFields().also { cachedFields = it }

    private fun computeFields(): Map<ConfigKey, ConfigValue> =
        fieldDefinitions
            .mapNotNull { definition ->
                val state = states[definition.key] ?: return@mapNotNull null
                if (definition.isNullable && !state.hasUserInput) return@mapNotNull null
                state.getValidatedValue()?.let { definition.key to it.toConfigValue() }
            }.toMap()

    fun toRow(): ListRow = ListRow(fields())

    /** Validates every field in this row, returning true only when all of them pass. */
    fun validate(): Boolean = states.values.map { it.validate() }.all { it }

    fun resetValidationErrors() {
        states.values.forEach { it.resetValidationErrors() }
    }

    /**
     * Whether the user has entered nothing at all in this row — which is how an untouched row is told
     * apart from a partially filled one, so the former is dropped and the latter is reported.
     *
     * Keyed off raw input rather than parsed values, so a row holding only input that does not parse yet
     * still counts as filled and gets a validation error instead of being silently discarded.
     */
    fun isEmpty(): Boolean = states.values.none { it.hasUserInput }
}
