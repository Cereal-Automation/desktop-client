package com.cereal.client.presentation.headless

import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.terminal.onTerminalSizeChanged
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import com.varabyte.kotter.platform.concurrent.locks.write
import com.varabyte.kotter.runtime.RunScope
import com.varabyte.kotter.runtime.terminal.Terminal
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(HeadlessTui::class.java)

private const val ESC = '\u001B'

/**
 * Paints [tui] on [terminal] as one Kotter section sized to the terminal and feeds it keys until it
 * quits, then runs [HeadlessTui.shutdown]. Shared by production (`SystemTerminal`) and the test
 * harness (in-memory terminal). Blocks until the session ends.
 *
 * [whileRunning] runs alongside the TUI inside the run block (the harness drives keys from it); the
 * session ends only once both the TUI has quit and [whileRunning] has returned.
 */
fun runTui(
    terminal: Terminal,
    tui: HeadlessTui,
    onRenderError: (Throwable) -> Unit = { logger.error("TUI render failed", it) },
    whileRunning: (suspend RunScope.() -> Unit)? = null,
) = session(terminal, clearTerminal = true, sectionExceptionHandler = onRenderError) {
    var links = emptyList<Pair<Int, String>>()
    section {
        val lines = tui.frame(width, height)
        links = lines.mapIndexedNotNull { i, line -> linkUrl(line)?.let { i to it } }
        // No newline after the last line: a trailing one would scroll the frame off the top.
        lines.forEachIndexed { i, line ->
            val shown = if (linkUrl(line) != null) "" else line
            if (i < lines.lastIndex) textLine(shown) else text(shown)
        }
    }.onRendered {
        // Kotter hard-wraps long text with real newlines, which breaks copying a URL. So link lines are
        // painted blank above and written here raw (with an OSC 8 hyperlink) over their reserved rows.
        // The frame fills the screen from the top row, hence the absolute cursor position.
        links.forEach { (row, url) -> terminal.write("${ESC}7$ESC[${row + 1};1H$ESC]8;;$url$ESC\\$url$ESC]8;;$ESC\\${ESC}8") }
    }.run {
        tui.onChanged = { rerender() }
        onKeyPressed { tui.onKey(key) }
        // Kotter erases the previous frame by line count, which is wrong once the terminal reflowed it,
        // so a resize wipes the screen and paints the frame from scratch.
        onTerminalSizeChanged {
            data.lock.write { terminal.clear() }
            rerender()
        }
        val extra = whileRunning?.let { block -> section.coroutineScope.async { runCatching { block() } } }
        try {
            tui.awaitQuit()
            tui.shutdown()
            extra?.await()?.getOrThrow()
        } finally {
            // Boot or sign-in may still report back after the session ends; the terminal is closed by then.
            tui.onChanged = {}
        }
    }
}
