package com.cereal.client.infrastructure.bootstrap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

class BootstrapPreferencesTest {
    @TempDir
    lateinit var homeDirectory: File

    private val file get() = File(homeDirectory, "bootstrap.properties")

    private fun preferences() = BootstrapPreferences(file)

    @Test
    fun `first run reports the key's default without creating a file`() {
        val value = preferences().get(BootstrapPreferenceKey.CrashReportingEnabled)

        assertTrue(value, "crash reporting is opt-out, so it defaults to enabled")
        assertFalse(file.exists(), "reading must not create the store")
    }

    @Test
    fun `a written value round-trips through a fresh instance`() {
        assertTrue(preferences().set(BootstrapPreferenceKey.CrashReportingEnabled, false))

        // A fresh instance proves the value came off disk rather than out of in-process state —
        // bootstrap and the settings UI are different instances in different phases of the launch.
        assertFalse(preferences().get(BootstrapPreferenceKey.CrashReportingEnabled))
    }

    @Test
    fun `a value can be written back to its default`() {
        preferences().set(BootstrapPreferenceKey.CrashReportingEnabled, false)
        preferences().set(BootstrapPreferenceKey.CrashReportingEnabled, true)

        assertTrue(preferences().get(BootstrapPreferenceKey.CrashReportingEnabled))
    }

    @Test
    fun `a corrupt store reads as the default and is repaired by the next write`() {
        // A malformed \u escape is what java.util.Properties actually rejects; a half-written file
        // from an interrupted launch is the realistic way to get one.
        file.writeText("crash_reporting_enabled=\\uZZZZ\n")

        assertTrue(
            preferences().get(BootstrapPreferenceKey.CrashReportingEnabled),
            "an unreadable store must fall back to the default, not crash the launch",
        )

        assertTrue(preferences().set(BootstrapPreferenceKey.CrashReportingEnabled, false))
        assertFalse(preferences().get(BootstrapPreferenceKey.CrashReportingEnabled))
    }

    @Test
    fun `an unparseable value for a known key reads as the default`() {
        file.writeText("crash_reporting_enabled=perhaps\n")

        assertTrue(preferences().get(BootstrapPreferenceKey.CrashReportingEnabled))
    }

    @Test
    fun `writing preserves unrelated keys already in the store`() {
        file.writeText("some_future_key=false\n")

        preferences().set(BootstrapPreferenceKey.CrashReportingEnabled, false)

        val stored = Properties().apply { file.inputStream().use { load(it) } }
        assertEquals("false", stored.getProperty("some_future_key"))
        assertEquals("false", stored.getProperty("crash_reporting_enabled"))
    }

    @Test
    fun `the store is created when the home directory does not exist yet`() {
        val store = BootstrapPreferences(File(homeDirectory, "Nested/Deeper/bootstrap.properties"))

        assertTrue(store.set(BootstrapPreferenceKey.CrashReportingEnabled, false))
        assertFalse(store.get(BootstrapPreferenceKey.CrashReportingEnabled))
    }

    @Test
    fun `a write that cannot land is reported rather than thrown`() {
        // The parent path is a *file*, so no directory can be created there and no temp file opened.
        val blocker = File(homeDirectory, "blocker").apply { writeText("") }
        val store = BootstrapPreferences(File(blocker, "bootstrap.properties"))

        assertFalse(store.set(BootstrapPreferenceKey.CrashReportingEnabled, false))
        assertTrue(store.get(BootstrapPreferenceKey.CrashReportingEnabled), "still the default")
    }
}
