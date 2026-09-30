package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import java.io.File

/** Where a TUI import reads from: text pasted into the terminal, or a file path in the data volume. */
sealed interface ImportSource {
    data class Pasted(
        val text: String,
    ) : ImportSource

    /** Relative paths resolve against the data volume; absolute ones are used as they are. */
    data class VolumePath(
        val path: String,
    ) : ImportSource
}

/**
 * Runs a file-based import [interactor] on [this] source, unchanged: a paste is written to a temp file
 * in the JVM temp directory (deleted in `finally`, whatever happens), a path resolves against [volume].
 * Pair a failure with [importError] for the message.
 *
 * ```
 * source.import(volume, readProxyFile) { file -> ReadProxyFileInteractor.Params(file, manifest) }
 *     .onSuccess { … }.onFailure { input.failed(importError(it)) }
 * ```
 */
suspend fun <T : Any, P> ImportSource.import(
    volume: File,
    interactor: Interactor<T, P>,
    params: (File) -> P,
): Result<T> {
    val file =
        when (this) {
            is ImportSource.Pasted -> File.createTempFile("cereal-paste-", ".txt")
            is ImportSource.VolumePath -> volume.toPath().resolve(path.trim()).toFile()
        }
    try {
        when (this) {
            is ImportSource.Pasted -> file.writeText(text)
            is ImportSource.VolumePath -> if (!file.isFile) return Result.failure(IllegalArgumentException("There is no file at ${file.path}."))
        }
        var result: Result<T> = Result.failure(IllegalStateException("The import didn't finish."))
        interactor(params(file)) {
            result =
                when (it) {
                    is SuspendableResult.Success -> Result.success(it.value)
                    is SuspendableResult.Failure -> Result.failure(it.error)
                }
        }
        return result
    } finally {
        if (this is ImportSource.Pasted) file.delete()
    }
}

private val DATA_ROW = Regex("^row (\\d+), (.*)$")
private val ON_LINE = Regex(" on line (\\d+)")
private val PROBLEM_COUNT = Regex("^(One problem was|\\d+ problems were) found:$")

/**
 * An import failure as `Line N: …` lines. The importers name a proxy file's line ("on line N") and a
 * CSV's 1-based data row ("row N, column 'key'"); a data row is file line N + 1 (the header is line 1).
 * ponytail: rewrites the existing messages; blank CSV rows the reader skips shift the numbers, so
 * report file lines from the reader itself if that ever matters.
 */
fun importError(error: Throwable): String =
    (error.message ?: error.toString())
        .removePrefix("The provided file is invalid: ")
        .lines()
        .filterNot { PROBLEM_COUNT.matches(it) }
        .joinToString("\n") { line ->
            DATA_ROW.matchEntire(line)?.let { "Line ${it.groupValues[1].toInt() + 1}: ${it.groupValues[2]}" }
                ?: ON_LINE.find(line)?.let { "Line ${it.groupValues[1]}: ${line.removeRange(it.range)}" }
                ?: line
        }

/**
 * The input step of a TUI import, drawn in place of a page body and given every key. [paste]: a
 * multi-line buffer (Enter is a new line, Ctrl-D imports, Esc cancels); otherwise one line holding a
 * path in the data volume (Enter imports). [submit] runs the import; report a failure back with
 * [failed], which reopens the input with the error under it (the pasted text is kept for fixing).
 */
class ImportInput(
    private val paste: Boolean,
    private val prompt: List<String>,
    private val submit: (ImportSource) -> Unit,
    private val cancel: () -> Unit,
) {
    private val text = StringBuilder()

    @Volatile private var error: String? = null

    @Volatile private var busy = false

    val keys: String
        get() =
            when {
                busy -> ""
                paste -> "paste, then Ctrl-D import · Esc cancel"
                else -> "Enter import · Esc cancel"
            }

    fun failed(message: String) {
        error = message
        busy = false
    }

    fun onKey(key: Key): Boolean {
        if (busy) return true
        error = null
        synchronized(text) {
            when {
                key == Keys.Escape -> cancel()
                key == Keys.Backspace -> if (text.isNotEmpty()) text.setLength(text.length - 1)
                paste && key == Keys.Eof -> start(ImportSource.Pasted(text.toString()))
                paste && key == Keys.Enter -> text.append('\n')
                paste && key == Keys.Tab -> text.append('\t')
                key == Keys.Enter -> start(ImportSource.VolumePath(text.toString()))
                key is CharKey -> text.append(key.char)
            }
        }
        return true
    }

    fun render(
        width: Int,
        height: Int,
    ): List<String> {
        val typed = synchronized(text) { text.toString() }
        val input =
            if (paste) {
                val lines = typed.lines()
                val count = if (typed.isEmpty()) 0 else lines.size
                listOf("  $count line(s) pasted:") + lines.takeLast(PASTE_TAIL).mapIndexed { i, line -> "  │ $line${if (i == minOf(lines.size, PASTE_TAIL) - 1) "_" else ""}" }
            } else {
                listOf("  Path in the data volume:", "  > ${typed}_")
            }
        val bottom = listOfNotNull(if (busy) "  Importing…" else null) + error?.lines().orEmpty().map { "  ! $it" }
        val top = prompt.map { "  $it" } + ""
        val room = (height - top.size - bottom.size).coerceAtLeast(1)
        return (top + input.takeLast(room) + bottom).map { HeadlessTui.truncate(it, width) }
    }

    private fun start(source: ImportSource) {
        if (text.isBlank()) return
        busy = true
        submit(source)
    }

    private companion object {
        const val PASTE_TAIL = 8
    }
}
