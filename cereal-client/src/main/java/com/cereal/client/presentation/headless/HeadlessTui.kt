package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.application.interactor.task.StopAllRunningTasksInteractor
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State holder for the TUI frame: tab bar, banner, body, two-line footer, and the quit flow.
 *
 * Kotter-free apart from the [Key] type, so the whole layout is a pure function of this state
 * ([frame]); [runTui] is the thin adapter that paints it and feeds keys in. Call [onChanged] after
 * any async state change so the adapter repaints.
 */
class HeadlessTui(
    scope: CoroutineScope,
    private val detachHint: String?,
    observeTasksInteractor: ObserveTasksInteractor,
    private val stopAllRunningTasksInteractor: StopAllRunningTasksInteractor,
    val tabs: List<TuiPage> = defaultTabs(),
) {
    private enum class QuitState { NONE, CONFIRMING, STOPPING }

    /** Set by the adapter; invoked whenever the frame needs repainting. */
    @Volatile
    var onChanged: () -> Unit = {}

    @Volatile
    private var selectedTab = 0

    @Volatile
    private var runningTasks = 0

    @Volatile
    private var quitState = QuitState.NONE

    private val quit = CompletableDeferred<Unit>()

    init {
        scope.launch {
            observeTasksInteractor(Interactor.None()).collect { result ->
                if (result is SuspendableResult.Success) {
                    runningTasks = result.value.count { it.status.isRunning() }
                    onChanged()
                }
            }
        }
    }

    fun frame(
        width: Int,
        height: Int,
    ): List<String> {
        val page = tabs[selectedTab]
        val header = listOf(tabBar(), banner())
        val footer =
            listOf(
                listOf("1-${tabs.size} tabs", page.keys).filter { it.isNotBlank() }.joinToString(" · "),
                listOfNotNull("q quit", detachHint?.let { "detach: $it" }).joinToString(" · "),
            )
        val bodyHeight = (height - header.size - footer.size).coerceAtLeast(0)
        val body = clip(page.body(width, bodyHeight), bodyHeight)
        val padding = List(bodyHeight - body.size) { "" }
        return (header + body + padding + footer).take(height).map { truncate(it, width) }
    }

    fun onKey(key: Key) {
        val char = (key as? CharKey)?.char
        when (quitState) {
            QuitState.STOPPING -> {
                return
            }

            QuitState.CONFIRMING -> {
                if (char == 'y' || char == 'Y') quit.complete(Unit) else quitState = QuitState.NONE
            }

            QuitState.NONE -> {
                if (tabs[selectedTab].onKey(key)) return onChanged()
                when (char) {
                    in '1'..('0' + tabs.size) -> selectedTab = char!! - '1'
                    'q' -> requestQuit()
                }
            }
        }
        onChanged()
    }

    /** Ctrl-C: same as `q`. */
    fun onInterrupt() {
        if (quitState == QuitState.NONE) requestQuit()
        onChanged()
    }

    suspend fun awaitQuit() = quit.await()

    /** Ends the session without asking (used by the harness and by process shutdown). */
    fun quitNow() {
        quit.complete(Unit)
    }

    /** Stops every running task through the normal stop path. Call once, after [awaitQuit]. */
    suspend fun shutdown() {
        quitState = QuitState.STOPPING
        onChanged()
        stopAllRunningTasksInteractor(Interactor.None())
    }

    private fun requestQuit() {
        if (runningTasks == 0) quit.complete(Unit) else quitState = QuitState.CONFIRMING
    }

    private fun tabBar(): String {
        val labels =
            tabs.mapIndexed { i, tab ->
                val label = "${i + 1} ${tab.title}"
                if (i == selectedTab) "[$label]" else label
            }
        val status = if (runningTasks > 0) " · $runningTasks running" else ""
        return labels.joinToString("  ") + status
    }

    private fun banner(): String =
        when (quitState) {
            QuitState.NONE -> {
                ""
            }

            QuitState.CONFIRMING -> {
                "Quit and stop $runningTasks running task(s)? [y/N]" +
                    (detachHint?.let { " · detach instead: $it" } ?: "")
            }

            QuitState.STOPPING -> {
                "Stopping tasks…"
            }
        }

    companion object {
        fun defaultTabs(): List<TuiPage> = listOf("Tasks", "Waiting", "Proxies", "Settings", "Notifications").map { PlaceholderPage(it) }

        /** The multiplexer key that detaches without stopping anything, or null when there is none. */
        fun detachHintFor(environment: Map<String, String>): String? =
            when {
                environment.containsKey("TMUX") -> "tmux prefix + d"
                environment.containsKey("STY") -> "Ctrl-A d"
                environment["CEREAL_DISTRIBUTION"] == "docker" -> "Ctrl-P Ctrl-Q"
                else -> null
            }

        /** Truncates to [width] (never wraps), marking the cut with an ellipsis. */
        fun truncate(
            line: String,
            width: Int,
        ): String = if (line.length <= width) line else line.take((width - 1).coerceAtLeast(0)) + "…"

        /** Clips to [height] lines, keeping the head and the tail around a `…` marker. */
        fun clip(
            lines: List<String>,
            height: Int,
        ): List<String> {
            if (lines.size <= height) return lines
            if (height < 3) return lines.take(height)
            val head = (height - 1) / 2
            val tail = height - 1 - head
            return lines.take(head) + "…" + lines.takeLast(tail)
        }
    }
}
