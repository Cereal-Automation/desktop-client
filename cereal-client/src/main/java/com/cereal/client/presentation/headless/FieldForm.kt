package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.Secret
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys

/**
 * The one field-list form every TUI form is drawn with (new-task configuration, List rows,
 * notification overrides, Settings): rows `› * Name  value  ! problem` on a [RowList].
 *
 * The form holds no values of its own. The owner rebuilds [rows] from its state on every `body()`
 * (so required markers, visibility and problems re-evaluate live) and receives every committed
 * change through [onChange]; the cursor follows the field key across rebuilds.
 *
 * ```
 * val form = FieldForm(onChange = { field, value -> values[field.key] = value })
 * form.rows = listOf(FieldForm.Header("h", "Section"), FieldForm.Field("k", "Name", values["k"], FieldForm.Editor.Line("text") { it.toConfigValue() }))
 * if (form.onKey(key)) return true          // ↑↓/Tab, Enter edit, Space toggle, ←→ cycle, the open input line
 * form.render(width, height)                 // rows + the selected field's description or the input line
 * form.keys                                  // this form's footer keys; append the owner's own
 * form.focus("k")                            // e.g. jump to the first problem
 * ```
 *
 * Editors: [Editor.Toggle] (Boolean, Space/Enter), [Editor.Cycle] (←→, `(none)` when nullable),
 * [Editor.Line] (Enter opens an inline input line; `parse` throws [IllegalArgumentException] with
 * the reason to reject, which keeps the line open; empty submits null), [Editor.Secret] (typed
 * masked, shown as `•••••• set`, never revealed; re-editing starts empty and an empty submit keeps the
 * value), [Editor.Open] (Enter opens the owner's picker or sub-screen) and [Editor.None] (display only). With [readOnly] nothing can be edited.
 */
class FieldForm(
    private val readOnly: Boolean = false,
    private val onChange: (field: Field, value: ConfigValue?) -> Unit = { _, _ -> },
) {
    sealed interface Row {
        val key: String
    }

    /** A section title; the cursor skips it. */
    data class Header(
        override val key: String,
        val text: String,
    ) : Row

    /**
     * @param display overrides the value column (e.g. "Residential EU (3 proxies)"); never pass a
     *   revealed secret here.
     */
    data class Field(
        override val key: String,
        val label: String,
        val value: ConfigValue?,
        val editor: Editor,
        val required: Boolean = false,
        val problem: String? = null,
        val description: String = "",
        val display: String? = null,
    ) : Row

    sealed interface Editor {
        data object Toggle : Editor

        data class Cycle(
            val options: List<ConfigValue>,
            val nullable: Boolean,
        ) : Editor

        /** [type] is shown next to the label in the input line, e.g. "whole number". */
        data class Line(
            val type: String,
            val parse: (String) -> ConfigValue?,
        ) : Editor

        data object Secret : Editor

        /** Enter calls [open] (a picker or sub-screen); show the value through [Field.display]. */
        class Open(
            val open: () -> Unit,
        ) : Editor

        data object None : Editor
    }

    private class Input(
        val field: Field,
        var text: String,
        var error: String? = null,
    )

    private val list = RowList<Row>()

    @Volatile private var input: Input? = null

    var rows: List<Row>
        get() = list.rows.map { it.item }
        set(value) {
            list.rows = value.map { RowList.Row(it.key, text(it), it) }
            if (list.selected is Header) skipHeaders(Keys.Down)
        }

    val selected: Field? get() = list.selected as? Field

    /** True while the inline input line is open: every key goes to it. */
    val editing: Boolean get() = input != null

    val keys: String
        get() =
            when {
                input != null -> "Enter save · Esc cancel"
                readOnly -> "↑↓ move"
                else -> "↑↓/Tab field · Enter edit · Space toggle · ←→ choose"
            }

    fun focus(key: String) = list.select(key)

    fun onKey(key: Key): Boolean {
        input?.let { return onInputKey(it, key) }
        val nav = if (key == Keys.Tab) Keys.Down else key
        if (list.onKey(nav)) {
            skipHeaders(nav)
            return true
        }
        val field = selected ?: return false
        if (readOnly) return false
        val editor = field.editor
        when {
            editor is Editor.Toggle && (key == Keys.Space || key == Keys.Enter) -> {
                onChange(field, ConfigValue.BooleanValue((field.value as? ConfigValue.BooleanValue)?.raw != true))
            }

            editor is Editor.Cycle && (key == Keys.Left || key == Keys.Right) -> {
                val choices = (if (editor.nullable) listOf(null) else emptyList()) + editor.options
                if (choices.isEmpty()) return true
                val at = choices.indexOf(field.value).coerceAtLeast(0)
                onChange(field, choices[(at + if (key == Keys.Right) 1 else -1).mod(choices.size)])
            }

            editor is Editor.Line && key == Keys.Enter -> {
                input =
                    Input(
                        field,
                        field.value
                            ?.raw
                            ?.toString()
                            .orEmpty(),
                    )
            }

            editor is Editor.Secret && key == Keys.Enter -> {
                input = Input(field, "")
            }

            editor is Editor.Open && key == Keys.Enter -> {
                editor.open()
            }

            else -> {
                return false
            }
        }
        return true
    }

    /** The rows, then either the selected field's description or the open input line. */
    fun render(
        width: Int,
        height: Int,
    ): List<String> {
        val input = input
        val bottom =
            if (input != null) {
                val field = input.field
                val secret = field.editor is Editor.Secret
                val type = (field.editor as? Editor.Line)?.type ?: "secret"
                listOfNotNull(
                    "",
                    "  ${field.label} ($type)${if (field.required) " *" else ""}",
                    "  > ${if (secret) "*".repeat(input.text.length) else input.text}_",
                    input.error?.let { "  ! $it" },
                    if (secret) "  Typed as *. Enter with nothing typed keeps the current value." else "  Empty clears the value.",
                )
            } else {
                selected
                    ?.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listOf("", "  $it") }
                    .orEmpty()
            }
        val main = list.render(width, (height - bottom.size).coerceAtLeast(0))
        return main + List((height - bottom.size - main.size).coerceAtLeast(0)) { "" } + bottom.map { HeadlessTui.truncate(it, width) }
    }

    private fun onInputKey(
        input: Input,
        key: Key,
    ): Boolean {
        when (key) {
            Keys.Escape -> {
                this.input = null
            }

            Keys.Backspace -> {
                input.text = input.text.dropLast(1)
                input.error = null
            }

            Keys.Enter -> {
                submit(input)
            }

            is CharKey -> {
                input.text += key.char
                input.error = null
            }
        }
        return true
    }

    private fun submit(input: Input) {
        val field = input.field
        when (val editor = field.editor) {
            is Editor.Secret -> {
                if (input.text.isNotEmpty()) onChange(field, ConfigValue.SecretValue(Secret(input.text)))
            }

            is Editor.Line -> {
                val value =
                    try {
                        input.text.takeIf { it.isNotBlank() }?.let(editor.parse)
                    } catch (e: IllegalArgumentException) {
                        input.error = e.message
                        return
                    }
                onChange(field, value)
            }

            else -> {
                Unit
            }
        }
        this.input = null
    }

    /** Moves off a header row: the way [key] went first, else the other way. */
    private fun skipHeaders(key: Key) {
        val rows = list.rows
        val at = rows.indexOfFirst { it.item === list.selected }
        if (at < 0 || rows[at].item !is Header) return
        val up = (at - 1 downTo 0).toList()
        val down = (at + 1..rows.lastIndex).toList()
        val order = if (key == Keys.Up || key == Keys.PageUp) up + down else down + up
        order.firstOrNull { rows[it].item !is Header }?.let { list.select(rows[it].key) }
    }

    private fun text(row: Row): String =
        when (row) {
            is Header -> row.text
            is Field -> "${if (row.required) "*" else " "} ${row.label.padEnd(LABEL_WIDTH)} ${valueText(row)}" + row.problem?.let { "  ! $it" }.orEmpty()
        }

    private fun valueText(field: Field): String {
        field.display?.let { return it }
        val value = field.value
        return when {
            field.editor is Editor.Cycle -> "‹ ${value?.let(::name) ?: "(none)"} ›"
            value == null -> "not set"
            field.editor is Editor.Toggle -> if ((value as? ConfigValue.BooleanValue)?.raw == true) "[x] yes" else "[ ] no"
            value is ConfigValue.SecretValue || field.editor is Editor.Secret -> SECRET_SET
            else -> HeadlessTui.truncate(value.raw.toString(), VALUE_WIDTH)
        }
    }

    private fun name(value: ConfigValue): String = (value as? ConfigValue.EnumValue)?.raw?.name ?: value.raw.toString()

    companion object {
        const val SECRET_SET = "•••••• set"
        private const val LABEL_WIDTH = 24
        private const val VALUE_WIDTH = 30
    }
}
