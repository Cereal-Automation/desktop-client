package testutil

import com.cereal.client.application.interactor.notification.SendRestartReportInteractor
import com.cereal.client.domain.model.notification.Notification
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.infrastructure.di.Injector
import com.cereal.client.infrastructure.di.modules.HeadlessModule
import com.cereal.client.infrastructure.di.modules.InMemoryProviderModule
import com.cereal.client.infrastructure.di.modules.InMemoryRepositoryModule
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.cereal.client.presentation.headless.HeadlessTui
import com.cereal.client.presentation.headless.runTui
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.platform.concurrent.locks.read
import com.varabyte.kotter.runtime.RunScope
import com.varabyte.kotter.runtime.terminal.TerminalSize
import com.varabyte.kotter.runtime.terminal.inmemory.InMemoryTerminal
import com.varabyte.kotter.runtime.terminal.inmemory.press
import com.varabyte.kotter.runtime.terminal.inmemory.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Drives headless mode the way a user does: the real interactor graph with every repository and
 * provider replaced by its in-memory implementation, rendered by the real [runTui] adapter into
 * Kotter's in-memory terminal.
 *
 * [seed] runs against the fresh Koin container before the TUI is created (seed in-memory
 * repositories here). [block] then runs alongside the live TUI: press keys, type text, and assert on
 * [HeadlessTestScope.screen]. The session ends when the TUI quits (e.g. the test pressed `q`) or
 * when [block] returns, whichever is later.
 *
 * ```
 * @Test fun `switches tab`() = runHeadlessTest {
 *     press(CharKey('3'))
 *     awaitScreen { it.first().contains("[3 Proxies]") }
 * }
 * ```
 */
fun runHeadlessTest(
    size: TerminalSize = TerminalSize(80, 24),
    environment: Map<String, String> = emptyMap(),
    seed: suspend Koin.() -> Unit = {},
    block: suspend HeadlessTestScope.() -> Unit,
) {
    // Defend against a previous test that failed before tearing Koin down.
    stopKoin()
    val koin =
        startKoin {
            modules(Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules) + HeadlessModule.modules)
        }.koin
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
        runBlocking { koin.seed() }
        val tui = koin.get<HeadlessTui> { parametersOf(scope, environment) }
        val terminal = InMemoryTerminal(size)
        val renderErrors = mutableListOf<Throwable>()
        runTui(terminal, tui, onRenderError = { synchronized(renderErrors) { renderErrors += it } }) {
            try {
                HeadlessTestScope(koin, terminal, tui, this).block()
            } finally {
                tui.quitNow()
            }
        }
        synchronized(renderErrors) { renderErrors.firstOrNull()?.let { throw AssertionError("TUI render failed", it) } }
    } finally {
        scope.cancel()
        stopKoin()
    }
}

class HeadlessTestScope(
    val koin: Koin,
    val terminal: InMemoryTerminal,
    val tui: HeadlessTui,
    private val runScope: RunScope,
) {
    inline fun <reified T : Any> get(): T = koin.get()

    /** The notifications sent so far, leaving out the restart report every signed-in boot sends. */
    fun sentNotifications(): List<Notification> =
        (koin.get<NotificationProvider>() as InMemoryNotificationProvider).sent.toList().filterNot {
            SendRestartReportInteractor.TITLE in it.toString()
        }

    suspend fun press(vararg keys: Key) = terminal.press(*keys)

    suspend fun type(text: String) = terminal.type(text)

    /** Simulates a terminal resize (SIGWINCH). */
    fun resize(
        width: Int,
        height: Int,
    ) {
        terminal.size = TerminalSize(width, height)
    }

    /** The screen as the user sees it now: one entry per terminal row, formatting stripped. */
    fun screen(): List<String> = runScope.data.lock.read { resolveScreen(terminal.buffer.toString()) }

    /** Waits until [condition] holds for the rendered screen, failing with the last screen on timeout. */
    suspend fun awaitScreen(
        timeout: Duration = 2.seconds,
        condition: (List<String>) -> Boolean,
    ): List<String> {
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            val lines = screen()
            if (condition(lines)) return lines
            if (start.elapsedNow() > timeout) {
                throw AssertionError("Screen never matched. Last screen:\n" + lines.joinToString("\n") { "|$it|" })
            }
            delay(POLL_MS)
        }
    }

    /** Waits until some row of the screen contains [text]. */
    suspend fun awaitText(text: String): List<String> = awaitScreen { lines -> lines.any { text in it } }

    private companion object {
        const val POLL_MS = 10L
    }
}

private const val ESC = '\u001B'

/**
 * Replays Kotter's output onto a virtual screen. Handles exactly what Kotter's section renderer
 * emits: text, `\r`, `\n`, erase-to-line-end (`CSI 0K`) and move-to-previous-line (`CSI 1F`, clamped
 * at the top row, as a real terminal does after a clear), plus the cursor save/move/restore (`ESC 7`,
 * `CSI row;1H`, `ESC 8`) that link lines are written with. Other escape sequences are formatting and
 * are dropped. Nothing wraps: a line longer than the terminal stays one row.
 *
 * A terminal emulator is one dispatch over escape sequences; splitting it would scatter the cursor state.
 */
@Suppress("CyclomaticComplexMethod")
private fun resolveScreen(output: String): List<String> {
    val rows = mutableListOf(StringBuilder())
    var row = 0
    var col = 0
    var saved = 0 to 0
    var i = 0
    val text = output
    while (i < text.length) {
        val c = text[i]
        i =
            when {
                c == ESC && i + 1 < text.length && text[i + 1] == '[' -> {
                    var end = i + 2
                    while (end < text.length && text[end] !in '@'..'~') end++
                    val params = text.substring(i + 2, end)
                    when (text.getOrNull(end)) {
                        'K' -> {
                            if (params == "" || params == "0") rows[row].setLength(minOf(col, rows[row].length))
                        }

                        'F' -> {
                            row = (row - (params.toIntOrNull() ?: 1)).coerceAtLeast(0)
                            col = 0
                        }

                        'H' -> {
                            row = (params.substringBefore(';').toIntOrNull() ?: 1) - 1
                            col = (params.substringAfter(';', "").toIntOrNull() ?: 1) - 1
                            while (rows.size <= row) rows += StringBuilder()
                        }
                    }
                    end + 1
                }

                c == ESC && text.getOrNull(i + 1) == '7' -> {
                    saved = row to col
                    i + 2
                }

                c == ESC && text.getOrNull(i + 1) == '8' -> {
                    row = saved.first
                    col = saved.second
                    i + 2
                }

                c == ESC -> {
                    // OSC or other: skip to the string terminator (ESC \) or BEL.
                    var end = i + 1
                    while (end < text.length && !text.isStringTerminatorAt(end)) end++
                    if (text.getOrNull(end) == ESC) end + 2 else end + 1
                }

                c == '\r' -> {
                    col = 0
                    i + 1
                }

                c == '\n' -> {
                    row++
                    col = 0
                    if (row == rows.size) rows += StringBuilder()
                    i + 1
                }

                else -> {
                    val line = rows[row]
                    while (line.length < col) line.append(' ')
                    if (col < line.length) line.setCharAt(col, c) else line.append(c)
                    col++
                    i + 1
                }
            }
    }
    return rows.map { it.toString() }
}

/** BEL, or ESC followed by a backslash, at [index]: the end of an OSC string. */
private fun String.isStringTerminatorAt(index: Int): Boolean = this[index] == '\u0007' || (this[index] == ESC && getOrNull(index + 1) == '\\')
