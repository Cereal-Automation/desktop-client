package com.cereal.client.headless

import com.cereal.client.App
import com.cereal.client.application.task.TaskManager
import com.cereal.client.infrastructure.bootstrap.ApplicationHome
import com.cereal.client.infrastructure.di.modules.HeadlessModule
import com.cereal.client.infrastructure.headless.HeadlessProcess
import com.cereal.client.presentation.headless.HeadlessTui
import com.cereal.client.presentation.headless.runTui
import com.cereal.client.smoke.SmokeTest
import com.varabyte.kotter.terminal.system.SystemTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.parameter.parametersOf
import java.io.File

/**
 * Headless mode: the client started with `--headless`, running without a display with the TUI as its
 * only front end. Never auto-detected; the desktop path without the flag is untouched.
 */
object HeadlessMode {
    const val FLAG = "--headless"

    private const val NO_TTY_MESSAGE =
        "Cereal headless mode needs an interactive terminal. " +
            "Start the container with `docker run -it` (or `-dit` and `docker attach`), " +
            "or run it inside tmux or screen."

    private const val ALREADY_RUNNING_MESSAGE =
        "Cereal is already running on this data directory. " +
            "Reattach to it (`docker attach <container>` or `tmux attach`) instead of starting a second instance."

    fun isRequested(args: Array<String>) = FLAG in args

    /** Runs headless mode to completion and returns the process exit code. */
    fun run(): Int {
        // Before anything can touch AWT.
        System.setProperty("java.awt.headless", "true")

        // Release gate inside the image: the same smoke run as the desktop build, plus the headless probe.
        if (SmokeTest.isRequested()) return SmokeTest.run(headless = true)

        if (!HeadlessProcess.hasTty()) {
            System.err.println(NO_TTY_MESSAGE)
            return 1
        }

        val home = ApplicationHome.directory
        if (!HeadlessProcess.lockDataDirectory(home)) {
            System.err.println(ALREADY_RUNNING_MESSAGE)
            return 1
        }

        HeadlessProcess.detachConsoleLogging()
        val originalErr = HeadlessProcess.redirectStderr(File(File(home, "Logs"), "stderr.log"))
        val errToFile = System.err
        try {
            val koin = App.initialize(listOf(HeadlessModule.modules))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val tui = koin.get<HeadlessTui> { parametersOf(scope, System.getenv()) }
            // SIGTERM (`docker stop`, reboot): end the jobs without persisting `Idle`, so the tasks resume on the next boot.
            // A TUI quit has already stopped them explicitly by then, so this finds nothing to do.
            val taskManager = koin.get<TaskManager>()
            Runtime.getRuntime().addShutdownHook(Thread { runBlocking { taskManager.shutdown() } })

            // Explicitly SystemTerminal: Kotter's default provider list falls back to a Swing window.
            val terminal = SystemTerminal()
            // SystemTerminal swaps System.err for a no-op stream; keep it going to Logs/stderr.log.
            System.setErr(errToFile)
            HeadlessProcess.onInterrupt { tui.onInterrupt() }
            // Ctrl-C as a key, not a SIGINT that also reaches Chrome (same foreground process group). Kotter
            // drops control bytes, so read it alongside.
            if (HeadlessProcess.disableTerminalSignals()) {
                scope.launch { terminal.read().collect { if (it == HeadlessProcess.CTRL_C) tui.onInterrupt() } }
            }

            runTui(terminal, tui)
        } finally {
            System.setErr(originalErr)
        }
        return 0
    }
}
