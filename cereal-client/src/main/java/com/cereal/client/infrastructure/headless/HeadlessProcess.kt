package com.cereal.client.infrastructure.headless

import ch.qos.logback.classic.LoggerContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sun.misc.Signal
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * Process-level plumbing for headless mode's boot order (TTY check, data-directory lock, keeping the
 * terminal to the TUI alone). Everything here runs before, or around, the TUI session.
 */
object HeadlessProcess {
    /**
     * Held for the life of the process: the OS releases the lock when the process ends, however it ends.
     * Kept in a field so the channel is never garbage collected (which would release the lock).
     */
    private var dataDirectoryLock: FileLock? = null

    /** On JDK 21 `System.console()` is only non-null when both stdin and stdout are a terminal. */
    fun hasTty(): Boolean = System.console() != null

    /**
     * Takes an exclusive lock on [homeDirectory] (via a lock file in it). Returns false when another
     * process already holds it.
     */
    fun lockDataDirectory(homeDirectory: File): Boolean {
        homeDirectory.mkdirs()
        val channel =
            FileChannel.open(
                File(homeDirectory, "cereal.lock").toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        val lock =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
        if (lock == null) {
            channel.close()
            return false
        }
        dataDirectoryLock = lock
        return true
    }

    /** Stops Logback writing to the console; the file appender keeps logging to `Logs/`. */
    fun detachConsoleLogging() {
        (LoggerFactory.getILoggerFactory() as? LoggerContext)
            ?.getLogger(Logger.ROOT_LOGGER_NAME)
            ?.detachAppender("STDOUT")
    }

    /** Points `System.err` at [file] (appending) and returns the stream it replaced. */
    fun redirectStderr(file: File): PrintStream {
        file.parentFile?.mkdirs()
        val original = System.err
        System.setErr(PrintStream(FileOutputStream(file, true), true))
        return original
    }

    /**
     * Routes SIGINT (Ctrl-C) to [handler] instead of the terminal library's default, which exits the
     * process on the spot.
     */
    fun onInterrupt(handler: () -> Unit) {
        Signal.handle(Signal("INT")) { handler() }
    }

    /**
     * Turns off the terminal's signal keys (`stty -isig`), so Ctrl-C arrives as the byte [CTRL_C] instead of
     * a SIGINT to the whole foreground process group, which would also kill the tasks' Chrome. The terminal
     * library restores the setting when it closes. Returns false when `stty` fails; SIGINT then still works.
     */
    fun disableTerminalSignals(): Boolean =
        try {
            ProcessBuilder("stty", "-isig")
                .redirectInput(File("/dev/tty"))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor() == 0
        } catch (e: IOException) {
            LoggerFactory.getLogger(HeadlessProcess::class.java).warn("Could not turn off the terminal's signal keys", e)
            false
        }

    const val CTRL_C = 3
}
