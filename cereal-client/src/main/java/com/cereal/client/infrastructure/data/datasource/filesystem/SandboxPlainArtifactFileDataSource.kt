package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [ArtifactFileDataSource] for the sandboxed `mock` flavor that stores bytes **unencrypted** under
 * `<baseDirectory>/<userId>/<relativePath>`, so they can be opened directly while debugging.
 * Dev-only: production uses the encrypted [FileSystemArtifactDataSource].
 */
class SandboxPlainArtifactFileDataSource(
    private val baseDirectory: File,
) : ArtifactFileDataSource {
    override suspend fun write(
        user: User,
        relativePath: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val file = File(userDirectory(user), relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun read(
        user: User,
        relativePath: String,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            File(userDirectory(user), relativePath).takeIf { it.exists() }?.readBytes()
        }

    override suspend fun copyToFile(
        user: User,
        relativePath: String,
        destination: File,
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        File(userDirectory(user), relativePath).copyTo(destination, overwrite = true)
        Unit
    }

    override suspend fun delete(
        user: User,
        relativePath: String,
    ) = withContext(Dispatchers.IO) {
        File(userDirectory(user), relativePath).delete()
        Unit
    }

    override suspend fun deleteForTask(
        user: User,
        taskId: String,
    ) = withContext(Dispatchers.IO) {
        File(userDirectory(user), taskId).deleteRecursively()
        Unit
    }

    private fun userDirectory(user: User) = File(baseDirectory, user.id)
}
