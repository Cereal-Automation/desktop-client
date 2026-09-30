package com.cereal.client.presentation.headless

import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys

/**
 * The one selectable row list every TUI list is built on: a cursor over [rows] that survives
 * updates (it follows the selected row's [Row.key]) and a window that scrolls to keep it visible.
 *
 * ```
 * val list = RowList<Proxy>()
 * list.rows = proxies.map { RowList.Row(it.id, it.label, it) }   // on every data change
 * if (list.onKey(key)) return true                              // Up/Down/PageUp/PageDown/Home/End
 * list.selected                                                 // the item under the cursor, or null
 * list.render(width, height)                                    // exactly min(rows, height) lines
 * ```
 */
class RowList<T> {
    /** [text] is drawn after the cursor column; [key] identifies the row across updates. */
    data class Row<T>(
        val key: String,
        val text: String,
        val item: T,
    )

    @Volatile
    private var index = 0

    @Volatile
    private var top = 0

    @Volatile
    private var pageSize = 1

    /** The row the user chose; kept while it is briefly missing (e.g. mid-move between groups). */
    @Volatile
    private var chosenKey: String? = null

    @Volatile
    var rows: List<Row<T>> = emptyList()
        set(value) {
            if (chosenKey == null) chosenKey = field.getOrNull(index)?.key
            field = value
            index = value.indexOfFirst { it.key == chosenKey }.takeIf { it >= 0 } ?: index.coerceIn(0, (value.size - 1).coerceAtLeast(0))
        }

    val selected: T? get() = rows.getOrNull(index)?.item

    /** Moves the cursor to the row with [key], if present. */
    fun select(key: String) {
        rows.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let {
            index = it
            chosenKey = key
        }
    }

    /** Handles the navigation keys; returns false for anything else. */
    fun onKey(key: Key): Boolean {
        val last = rows.lastIndex.coerceAtLeast(0)
        index =
            when (key) {
                Keys.Up -> index - 1
                Keys.Down -> index + 1
                Keys.PageUp -> index - pageSize
                Keys.PageDown -> index + pageSize
                Keys.Home -> 0
                Keys.End -> last
                else -> return false
            }.coerceIn(0, last)
        chosenKey = rows.getOrNull(index)?.key
        return true
    }

    /** The visible window: at most [height] lines, the selected one marked with `›`. */
    fun render(
        width: Int,
        height: Int,
    ): List<String> {
        val rows = rows
        pageSize = height.coerceAtLeast(1)
        top = top.coerceIn((index - pageSize + 1).coerceAtLeast(0), index.coerceAtLeast(0))
        return rows.drop(top).take(pageSize).mapIndexed { i, row ->
            HeadlessTui.truncate((if (top + i == index) "› " else "  ") + row.text, width)
        }
    }
}
