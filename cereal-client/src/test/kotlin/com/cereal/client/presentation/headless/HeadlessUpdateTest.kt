package com.cereal.client.presentation.headless

import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAppUpdateProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import com.varabyte.kotter.foundation.input.CharKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import testutil.HeadlessTestScope
import testutil.runHeadlessTest

class HeadlessUpdateTest {
    private fun latest(
        version: String,
        minRequired: String = "1.0.0",
    ): suspend Koin.() -> Unit =
        {
            (get<AppUpdateProvider>() as InMemoryAppUpdateProvider).latestVersion =
                InMemoryAppUpdateProvider.version(version, minRequired)
        }

    private fun HeadlessTestScope.assertNothingDownloadedOrInstalled() {
        assertEquals(emptyList<Any>(), (get<AppUpdateProvider>() as InMemoryAppUpdateProvider).downloads)
        assertEquals(emptyList<Any>(), (get<SystemProvider>() as InMemorySystemProvider).installedUpdates)
    }

    @Test
    fun `no banner when up to date and U does nothing`() =
        runHeadlessTest {
            val screen = awaitText("1-5 tabs")
            assertEquals("", screen[1])

            press(CharKey('U'))

            awaitScreen { it.first().contains("[1 Tasks]") && it[1].isEmpty() }
        }

    @Test
    fun `an available update shows the banner and U shows the Docker commands`() =
        runHeadlessTest(environment = mapOf("CEREAL_DISTRIBUTION" to "docker"), seed = latest("1.2.0")) {
            val tabs = awaitText("1-5 tabs")
            assertEquals("Update 1.2.0 available. Press U for the upgrade commands.", tabs[1])

            press(CharKey('U'))

            val commands = awaitText("Upgrade to 1.2.0")
            assertTrue(commands.any { "docker pull ghcr.io/cereal-automation/cereal:1.2.0" in it }, commands.joinToString("\n"))
            assertTrue(commands.any { "docker rm -f" in it })
            assertTrue(commands.any { "any key back" in it })

            press(CharKey('x'))

            awaitText("[1 Tasks]")
            assertNothingDownloadedOrInstalled()
        }

    @Test
    fun `U on an AppImage says to download the new AppImage`() =
        runHeadlessTest(environment = mapOf("APPIMAGE" to "/opt/Cereal.AppImage"), seed = latest("1.2.0")) {
            awaitText("Press U for the upgrade commands.")

            press(CharKey('U'))

            val screen = awaitText("Download the new AppImage (Cereal 1.2.0)")
            assertTrue(screen.any { "replace /opt/Cereal.AppImage" in it }, screen.joinToString("\n"))
            assertTrue(screen.none { "docker" in it })
        }

    @Test
    fun `a required update blocks every tab and task start`() =
        runHeadlessTest(environment = mapOf("CEREAL_DISTRIBUTION" to "docker"), seed = latest("2.0.0", minRequired = "2.0.0")) {
            val screen = awaitText("No tasks can start on this version.")
            val text = screen.joinToString("\n")
            assertEquals("Update required", screen.first())
            assertTrue("Cereal 2.0.0 is required." in text, text)
            assertTrue("docker pull ghcr.io/cereal-automation/cereal:2.0.0" in text, text)
            assertTrue(screen.any { it.startsWith("q quit") })

            press(CharKey('1'), CharKey('U'))

            awaitScreen { it.first() == "Update required" && it.none { line -> "Upgrade to" in line } }
            assertNothingDownloadedOrInstalled()

            press(CharKey('q'))
            tui.awaitQuit()
        }
}
