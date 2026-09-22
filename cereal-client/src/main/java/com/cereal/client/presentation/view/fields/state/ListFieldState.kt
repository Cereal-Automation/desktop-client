package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.cereal.client.domain.model.script.configuration.ConfigKey
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.view.fields.validator.FieldValidator

/**
 * Form state for a list configuration item: a growable list of rows, each holding one form field
 * state per field of the record.
 *
 * Rows are built by [createRow] rather than here, because a row's field states are produced by the same
 * mapper that builds every other configuration field — so a record field gets exactly the widget and
 * validator its type already has at top level.
 */
class ListFieldState(
    val fieldDefinitions: List<ScriptConfigurationItemDefinition>,
    private val createRow: (initialFields: Map<ConfigKey, ConfigValue>, onRowValueChange: () -> Unit) -> ListRowState,
    validators: List<FieldValidator<ListRows>> = emptyList(),
    initialValue: ListRows = ListRows.EMPTY,
    private val onValueChange: ((ListFieldState) -> Unit)? = null,
    private val onImport: ((ListFieldState) -> Unit)? = null,
) : FormFieldState<ListRows, ListRows?>(validators) {
    val rows: SnapshotStateList<ListRowState> =
        initialValue.rows
            .map { row -> newRow(row.fields) }
            .ifEmpty { listOf(newRow(emptyMap())) }
            .toMutableStateList()

    /**
     * The rows the user actually entered something in. Rows they never touched are excluded, so a nullable
     * list left untouched counts as no value at all, while a row with *any* value in it is kept — even when
     * incomplete — so validation can point at the missing field instead of discarding what was typed.
     */
    override val fieldValue: ListRows
        get() = ListRows(rows.filterNot { it.isEmpty() }.map { it.toRow() })

    override fun getValidatedValue(): ListRows? = fieldValue.takeIf { it.rows.isNotEmpty() }

    fun addRow() {
        rows.add(newRow(emptyMap()))
        notifyValueChanged()
    }

    fun removeRow(index: Int) {
        if (rows.size > 1) {
            rows.removeAt(index)
        } else {
            // Keep one row on screen so the fields stay reachable; clearing it is how a user empties an
            // optional list.
            rows[0] = newRow(emptyMap())
        }
        notifyValueChanged()
    }

    /**
     * How many rows the user has actually entered something in. Zero for a list showing only the
     * untouched blank starter row, which is why importing into an empty list needs no confirmation.
     */
    fun enteredRowCount(): Int = rows.count { !it.isEmpty() }

    fun showImportButton(): Boolean = onImport != null

    fun onImport() {
        onImport?.invoke(this)
    }

    /**
     * Replaces every row with imported ones, then clears any error left over from the previous
     * contents and re-evaluates against what just arrived — imported rows are ordinary rows and are
     * held to exactly the same validation.
     */
    fun replaceRows(value: ListRows) {
        setRows(value)
        resetValidationErrors()
        validate()
    }

    /** Replaces every row, used when copying the configuration of an existing script instance. */
    fun setRows(value: ListRows) {
        rows.clear()
        rows.addAll(value.rows.map { row -> newRow(row.fields) }.ifEmpty { listOf(newRow(emptyMap())) })
        notifyValueChanged()
    }

    override fun validate(): Boolean {
        // Both halves must run: a row's own field errors are what the user sees next to the offending
        // field, while the list-level error covers cardinality. Untouched rows are skipped — an empty row
        // is not data the user is being asked to complete, it is a row they have not started.
        rows.filter { it.isEmpty() }.forEach { it.resetValidationErrors() }
        val rowsValid = rows.filterNot { it.isEmpty() }.map { it.validate() }.all { it }
        val listValid = super.validate()
        return rowsValid && listValid
    }

    override fun resetValidationErrors() {
        super.resetValidationErrors()
        rows.forEach { it.resetValidationErrors() }
    }

    private fun newRow(initialFields: Map<ConfigKey, ConfigValue>): ListRowState = createRow(initialFields) { notifyValueChanged() }

    private fun notifyValueChanged() {
        onValueChange?.invoke(this)
    }
}
