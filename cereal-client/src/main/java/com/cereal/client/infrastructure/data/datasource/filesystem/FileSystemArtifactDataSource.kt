package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores artifact bytes under `<databaseDirectory>/Artifacts/<userId>/<taskId>/<artifactId>`, encrypted at rest with
 * the user's file-encryption key — the same AES/GCM scheme used for script jars (see ADR-0001).
 */
class FileSystemArtifactDataSource(
    private val config: ApplicationConfig,
) : ArtifactFileDataSource {
    override suspend fun write(
        user: User,
        relativePath: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val file = resolve(user, relativePath)
        file.parentFile?.mkdirs()
        val encrypted = Encryption.encryptBytes(bytes, fileKey(user))
        file.writeBytes(encrypted)
    }

    override suspend fun copyToFile(
        user: User,
        relativePath: String,
        destination: File,
    ) = withContext(Dispatchers.IO) {
        val encrypted = resolve(user, relativePath).readBytes()
        val decrypted = Encryption.decryptBytes(encrypted, fileKey(user))
        destination.parentFile?.mkdirs()
        destination.writeBytes(decrypted)
    }

    override suspend fun deleteForTask(
        user: User,
        taskId: String,
    ) = withContext(Dispatchers.IO) {
        File(userDirectory(user), taskId).deleteRecursively()
        Unit
    }

    private fun resolve(
        user: User,
        relativePath: String,
    ) = File(userDirectory(user), relativePath)

    private fun userDirectory(user: User) = File(File(config.databaseDirectory, ARTIFACTS_DIRECTORY), user.id)

    private fun fileKey(user: User) = Encryption.getEncryptionKey(config.fileEncryptionKey, user.encryptionKey, ENCRYPTION_KEY_SIZE_BYTES)

    private companion object {
        private const val ARTIFACTS_DIRECTORY = "Artifacts"
        private const val ENCRYPTION_KEY_SIZE_BYTES = 32
    }
}
