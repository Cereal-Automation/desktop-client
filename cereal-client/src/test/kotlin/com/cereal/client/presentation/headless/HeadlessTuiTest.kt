package com.cereal.client.presentation.headless

import com.cereal.client.domain.repository.TasksRepository
import com.varabyte.kotter.foundation.input.CharKey
import fixtures.aTask
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.runHeadlessTest

class HeadlessTuiTest {
    @Test
    fun `renders the frame at 80x24 with tabs, footer and quit hint`() =
        runHeadlessTest {
            val screen = awaitText("1-5 tabs")

            assertEquals(24, screen.size, screen.joinToString("\n") { "|$it|" })
            assertTrue(screen.all { it.length <= 80 }, screen.joinToString("\n") { "|$it|" })
            assertTrue(screen.first().contains("[1 Tasks]"), screen.joinToString("\n") { "|$it|" })
            assertTrue(screen.first().contains("5 Notifications"), screen.joinToString("\n") { "|$it|" })
            assertTrue(screen[22].startsWith("1-5 tabs · "), screen[22])
            assertEquals("q quit", screen[23])
        }

    @Test
    fun `number keys switch tabs`() =
        runHeadlessTest {
            awaitText("[1 Tasks]")

            press(CharKey('3'))

            val screen = awaitText("[3 Proxies]")
            assertFalse(screen.first().contains("[1 Tasks]"))
            awaitText("Proxy groups")
        }

    @Test
    fun `footer shows the Docker detach key`() =
        runHeadlessTest(environment = mapOf("CEREAL_DISTRIBUTION" to "docker")) {
            awaitText("q quit · detach: Ctrl-P Ctrl-Q")
        }

    @Test
    fun `footer shows the tmux detach key when TMUX is set`() =
        runHeadlessTest(environment = mapOf("TMUX" to "/tmp/tmux-1000/default,1,0", "CEREAL_DISTRIBUTION" to "docker")) {
            awaitText("q quit · detach: tmux prefix + d")
        }

    @Test
    fun `q quits straight away when no task runs`() =
        runHeadlessTest {
            awaitText("q quit")

            press(CharKey('q'))

            tui.awaitQuit()
        }

    @Test
    fun `quit with running tasks asks first, and confirming stops them`() {
        val job = Job()
        runHeadlessTest(
            environment = mapOf("CEREAL_DISTRIBUTION" to "docker"),
            seed = { get<TasksRepository>().addTask(aTask("t1", "com.a", running = true).copy(job = job)) },
        ) {
            awaitText("1 running")

            press(CharKey('q'))
            awaitText("Quit and stop 1 running task(s)? [y/N] · detach instead: Ctrl-P Ctrl-Q")

            press(CharKey('n'))
            awaitScreen { lines -> lines.none { "Quit and stop" in it } }
            assertTrue(job.isActive)

            press(CharKey('q'))
            awaitText("[y/N]")
            press(CharKey('y'))
            tui.awaitQuit()
        }
        assertTrue(job.isCancelled)
    }

    @Test
    fun `resize repaints the whole frame at the new size, truncating instead of wrapping`() =
        runHeadlessTest {
            awaitText("1-5 tabs")

            resize(40, 12)

            val screen = awaitScreen { it.size == 12 && it.last() == "q quit" }
            assertTrue(screen.all { it.length <= 40 }, screen.joinToString("\n"))
            assertTrue(screen.first().endsWith("…"))
        }

    @Test
    fun `clip keeps the head and the tail`() {
        val lines = (1..10).map { "line $it" }

        assertEquals(listOf("line 1", "line 2", "…", "line 9", "line 10"), HeadlessTui.clip(lines, 5))
        assertEquals(lines, HeadlessTui.clip(lines, 10))
    }
}
