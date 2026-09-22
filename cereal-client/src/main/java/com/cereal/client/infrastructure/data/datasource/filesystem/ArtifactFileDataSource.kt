package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.user.User
import java.io.File

/**
 * Stores artifact bytes encrypted at rest, one file per artifact, under a per-user, per-task directory.
 */
interface ArtifactFileDataSource {
    /** Encrypts [bytes] and writes them at [relativePath] under the user's artifact storage. */
    suspend fun write(
        user: User,
        relativePath: String,
        bytes: ByteArray,
    )

    /** Decrypts the artifact stored at [relativePath] and writes the plaintext bytes to [destination]. */
    suspend fun copyToFile(
        user: User,
        relativePath: String,
        destination: File,
    )

    /** Removes every stored artifact file belonging to [taskId]. */
    suspend fun deleteForTask(
        user: User,
        taskId: String,
    )
}
