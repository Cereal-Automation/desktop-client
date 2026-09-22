package com.cereal.client.infrastructure.crashreporting

import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The bootstrap half of the opt-out: what [com.cereal.client.App] does with the stored choice.
 */
class CrashReportingStartupTest {
    @TempDir
    lateinit var homeDirectory: File

    private val preferences get() = BootstrapPreferences(File(homeDirectory, "bootstrap.properties"))

    @Test
    fun `starts reporting on a first run, where no choice has been recorded`() {
        val lifecycle = RecordingCrashReportingLifecycle()

        CrashReportingClient.startIfEnabled(preferences, lifecycle)

        assertEquals(1, lifecycle.startCount, "crash reporting is opt-out, so it is on until switched off")
    }

    @Test
    fun `never starts reporting when the user has opted out`() {
        preferences.set(BootstrapPreferenceKey.CrashReportingEnabled, false)
        val lifecycle = RecordingCrashReportingLifecycle()

        CrashReportingClient.startIfEnabled(preferences, lifecycle)

        // The whole promise of the setting: not started at all, rather than started and filtered.
        assertEquals(0, lifecycle.startCount)
    }

    @Test
    fun `starts reporting again once the user opts back in`() {
        preferences.set(BootstrapPreferenceKey.CrashReportingEnabled, false)
        preferences.set(BootstrapPreferenceKey.CrashReportingEnabled, true)
        val lifecycle = RecordingCrashReportingLifecycle()

        CrashReportingClient.startIfEnabled(preferences, lifecycle)

        assertEquals(1, lifecycle.startCount)
    }
}
