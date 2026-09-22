package com.cereal.client.infrastructure.data.datasource.os

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class UpdaterArgumentsTest {
    @Test
    fun `toArgs then fromArgs round-trips all fields including the digest`() {
        val original =
            UpdaterArguments(
                parentPid = 4242L,
                installer = File("/tmp/cereal-client-latest.exe"),
                app = File("/tmp/Cereal.exe"),
                expectedSha256 = "abc123",
            )

        assertEquals(original, UpdaterArguments.fromArgs(original.toArgs().toTypedArray()))
    }

    @Test
    fun `a null digest round-trips through the sentinel back to null`() {
        val original =
            UpdaterArguments(
                parentPid = 1L,
                installer = File("/tmp/installer.exe"),
                app = File("/tmp/app.exe"),
                expectedSha256 = null,
            )

        val decoded = UpdaterArguments.fromArgs(original.toArgs().toTypedArray())

        assertNull(decoded?.expectedSha256)
        assertEquals(original, decoded)
    }

    @Test
    fun `fromArgs returns null when there are too few arguments`() {
        assertNull(UpdaterArguments.fromArgs(arrayOf("1", "/tmp/installer.exe", "/tmp/app.exe")))
    }

    @Test
    fun `fromArgs returns null when the parent pid is not numeric`() {
        assertNull(UpdaterArguments.fromArgs(arrayOf("not-a-pid", "/tmp/installer.exe", "/tmp/app.exe", "-")))
    }
}
