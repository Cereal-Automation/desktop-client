package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.app.Version
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/**
 * Filesystem-edge integration test: exercises the real staging-directory creation so the
 * owner-only permissions and stale-directory cleanup that close the TOCTOU window (CWE-367/377)
 * are verified against an actual filesystem.
 */
class FileSystemTempDataSourceTest {
    private fun version(downloadUrl: String = "https://downloads.test/cereal-client-2.1.0.dmg") =
        Version(
            version = SemVer.parse("2.1.0"),
            minRequiredVersion = SemVer.parse("1.0.0"),
            downloadUrl = downloadUrl,
        )

    @Test
    fun `destination filename is the last segment of the download url`(
        @TempDir base: Path,
    ) {
        val dataSource = FileSystemTempDataSource(base.toFile())

        val destination = dataSource.resolveAppBinaryDestination(version())

        assertEquals("cereal-client-2.1.0.dmg", destination.name)
    }

    @Test
    fun `destination is staged in a fresh private directory under the base directory`(
        @TempDir base: Path,
    ) {
        val dataSource = FileSystemTempDataSource(base.toFile())

        val destination = dataSource.resolveAppBinaryDestination(version())

        val stagingDirectory = destination.parentFile
        assertTrue(stagingDirectory.exists())
        assertEquals(base.toFile(), stagingDirectory.parentFile)
        // The directory is fresh, so the destination file itself does not exist yet.
        assertFalse(destination.exists())
    }

    @Test
    fun `staging directory is only accessible by the owner`(
        @TempDir base: Path,
    ) {
        assumeTrue(
            base.fileSystem.supportedFileAttributeViews().contains("posix"),
            "POSIX permissions are not supported on this filesystem",
        )
        val dataSource = FileSystemTempDataSource(base.toFile())

        val destination = dataSource.resolveAppBinaryDestination(version())

        val permissions = Files.getPosixFilePermissions(destination.parentFile.toPath())
        val groupOrOther =
            permissions.filterNot {
                it in
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    )
            }
        assertTrue(groupOrOther.isEmpty(), "staging directory grants non-owner access: $groupOrOther")
    }

    @Test
    fun `each resolution gets its own staging directory`(
        @TempDir base: Path,
    ) {
        val dataSource = FileSystemTempDataSource(base.toFile())

        val first = dataSource.resolveAppBinaryDestination(version())
        val second = dataSource.resolveAppBinaryDestination(version())

        assertNotEquals(first.parentFile, second.parentFile)
    }

    @Test
    fun `stale staging directories from previous runs are cleaned up`(
        @TempDir base: Path,
    ) {
        val dataSource = FileSystemTempDataSource(base.toFile())
        val stale = dataSource.resolveAppBinaryDestination(version()).parentFile
        File(stale, "old-download.dmg").writeText("stale")
        // An unrelated directory in the base dir must be left alone.
        val unrelated = File(base.toFile(), "not-a-staging-dir").apply { mkdirs() }

        dataSource.resolveAppBinaryDestination(version())

        assertFalse(stale.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `base directory is created when it does not exist yet`(
        @TempDir tmp: Path,
    ) {
        val base = File(tmp.toFile(), "does/not/exist/yet")
        val dataSource = FileSystemTempDataSource(base)

        val destination = dataSource.resolveAppBinaryDestination(version())

        assertTrue(destination.parentFile.exists())
    }
}
