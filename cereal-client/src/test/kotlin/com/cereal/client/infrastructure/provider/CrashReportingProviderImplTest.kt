package com.cereal.client.infrastructure.provider

import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences
import com.cereal.client.infrastructure.crashreporting.RecordingCrashReportingLifecycle
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CrashReportingProviderImplTest {
    @TempDir
    lateinit var homeDirectory: File

    private val applicationConfig = InMemoryApplicationConfig()
    private val lifecycle = RecordingCrashReportingLifecycle()

    private fun provider(
        preferences: BootstrapPreferences = BootstrapPreferences(File(homeDirectory, "bootstrap.properties")),
    ) = CrashReportingProviderImpl(preferences, applicationConfig, lifecycle)

    @Test
    fun `reports enabled before the user has chosen anything`() {
        assertTrue(provider().isEnabled())
    }

    @Test
    fun `disabling stops the reporting client instead of leaving it running`() {
        provider().setEnabled(false)

        assertEquals(1, lifecycle.stopCount)
        assertEquals(0, lifecycle.startCount)
    }

    @Test
    fun `enabling starts the reporting client without a restart`() {
        provider().setEnabled(false)
        provider().setEnabled(true)

        assertEquals(1, lifecycle.startCount)
        assertEquals(
            applicationConfig.homeDirectory.path,
            lifecycle.lastHomeDirectory,
            "the restarted client should carry the report context the bootstrap one gained after DI",
        )
    }

    @Test
    fun `the choice survives a restart`() {
        provider().setEnabled(false)

        // A separate instance over the same file stands in for the next launch.
        assertFalse(provider().isEnabled())
    }
}
