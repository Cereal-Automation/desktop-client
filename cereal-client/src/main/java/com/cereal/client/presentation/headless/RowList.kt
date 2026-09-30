package com.cereal.client.presentation.headless

import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys

/**
 * The one selectable row list every TUI list is built on: a cursor over [rows] that survives
 * updates (it follows the selected row's [Row.key]) and a window that scrolls to keep it visible.
 * Thread-safe: rows arrive from data collectors and the render thread, keys from the input thread.
 *
 * ```
 * val list = RowList<Proxy>()
 * list.rows = proxies.map { RowList.Row(it.id, it.label, it) }   // on every data change
 * if (list.onKey(key)) return true                              // Up/Down/PageUp/PageDown/Home/End
 * list.selected                                                 // the item under the cursor, or null
 * list.render(width, height)                                    // at most height lines
 * ```
 */
class RowList<T> {
    /**
     * [text] is drawn after the cursor column; [key] identifies the row across updates. [detail] lines (an
     * expanded row) are drawn as they are below it, and kept on screen with it when it is selected.
     */
    data class Row<T>(
        val key: String,
        val text: String,
        val item: T,
        val detail: List<String> = emptyList(),
    )

    private var index = 0

    private var top = 0

    private var pageSize = 1

    /** The row the user chose; kept while it is briefly missing (e.g. mid-move between groups). */
    private var chosenKey: String? = null

    @get:Synchronized
    @set:Synchronized
    var rows: List<Row<T>> = emptyList()
        set(value) {
            if (chosenKey == null) chosenKey = field.getOrNull(index)?.key
            field = value
            index = value.indexOfFirst { it.key == chosenKey }.takeIf { it >= 0 } ?: index.coerceIn(0, (value.size - 1).coerceAtLeast(0))
        }

    val selected: T?
        @Synchronized get() = rows.getOrNull(index)?.item

    /** Moves the cursor to the row with [key], if present. */
    @Synchronized
    fun select(key: String) {
        rows.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let {
            index = it
            chosenKey = key
        }
    }

    /** Handles the navigation keys; returns false for anything else. */
    @Synchronized
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

    /** The visible window: at most [height] lines, the selected row marked with `›`. */
    @Synchronized
    fun render(
        width: Int,
        height: Int,
    ): List<String> {
        if (height <= 0) return emptyList()
        pageSize = height
        // Scroll just far enough to keep the selected row, and its detail lines, on screen.
        top = top.coerceIn(0, index.coerceAtLeast(0))
        while (top < index && rows.subList(top, index + 1).sumOf { 1 + it.detail.size } > height) top++
        return rows
            .drop(top)
            .flatMapIndexed { i, row ->
                listOf(HeadlessTui.truncate((if (top + i == index) "› " else "  ") + row.text, width)) +
                    row.detail.map { HeadlessTui.truncate(it, width) }
            }.take(height)
    }
}
