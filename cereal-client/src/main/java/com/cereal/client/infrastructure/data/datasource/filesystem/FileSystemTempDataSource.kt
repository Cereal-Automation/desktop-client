package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.app.Version
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Resolves where downloaded installer binaries are staged.
 *
 * Downloads are staged in a freshly created, owner-only (0700) directory under
 * [stagingBaseDirectory] — a per-user application directory — rather than the shared,
 * world-writable system temp dir. In the shared temp dir the installer path is predictable and
 * writable by other local users, letting an attacker swap the file between its download-time hash
 * check and launch (CWE-367/CWE-377).
 */
class FileSystemTempDataSource(
    private val stagingBaseDirectory: File,
) {
    private val logger = LoggerFactory.getLogger(FileSystemTempDataSource::class.java)

    /**
     * Returns the [File] path where [version]'s installer binary should be written.
     *
     * The file may not exist yet — the caller is responsible for creating and writing it.
     * The filename is derived from the last path segment of [Version.downloadUrl], inside a fresh
     * private staging directory. Staging directories left behind by previous runs are cleaned up
     * best-effort first.
     */
    fun resolveAppBinaryDestination(version: Version): File {
        val filename = version.downloadUrl.substringAfterLast("/")
        cleanUpStaleStagingDirectories()
        val file = File(createPrivateStagingDirectory(), filename)
        logger.debug("Resolved app binary destination: $file")
        return file
    }

    private fun createPrivateStagingDirectory(): File {
        val base = stagingBaseDirectory.toPath()
        Files.createDirectories(base)
        val supportsPosix = base.fileSystem.supportedFileAttributeViews().contains("posix")
        val directory =
            if (supportsPosix) {
                Files.createTempDirectory(base, STAGING_DIR_PREFIX, OWNER_ONLY_DIRECTORY)
            } else {
                // Windows has no POSIX permissions; the directory inherits the ACLs of the
                // per-user base directory, which is already private to the current user.
                Files.createTempDirectory(base, STAGING_DIR_PREFIX)
            }
        return directory.toFile()
    }

    private fun cleanUpStaleStagingDirectories() {
        stagingBaseDirectory
            .listFiles { file -> file.isDirectory && file.name.startsWith(STAGING_DIR_PREFIX) }
            ?.forEach { stale ->
                if (!stale.deleteRecursively()) {
                    logger.debug("Could not delete stale update staging directory $stale")
                }
            }
    }

    private companion object {
        const val STAGING_DIR_PREFIX = "update-staging-"
        val OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    }
}
