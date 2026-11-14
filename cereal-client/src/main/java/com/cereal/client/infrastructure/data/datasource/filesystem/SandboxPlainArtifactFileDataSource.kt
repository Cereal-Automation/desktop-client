package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * [ArtifactFileDataSource] for the sandboxed `mock` flavor that stores bytes **unencrypted** under
 * `<baseDirectory>/<userId>/<relativePath>`, so they can be opened directly while debugging. Gzipped bytes (run
 * traces are gzipped JSON Lines) also get a decompressed `<relativePath>.jsonl` copy beside them, readable with
 * `cat`/`jq`; the gzipped original stays the source of truth, since it is what gets read back and reported.
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
        if (bytes.isGzip()) plainCopy(file).writeBytes(bytes.gunzipIfGzip())
        Unit
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
        val file = File(userDirectory(user), relativePath)
        file.delete()
        plainCopy(file).delete()
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

    private fun plainCopy(file: File) = File(file.path + PLAIN_COPY_EXTENSION)

    companion object {
        private const val PLAIN_COPY_EXTENSION = ".jsonl"

        /** Whether these bytes start with the gzip magic number. */
        fun ByteArray.isGzip() = size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()

        /** These bytes decompressed when gzipped, else as they are. */
        fun ByteArray.gunzipIfGzip(): ByteArray = if (isGzip()) GZIPInputStream(inputStream()).use { it.readBytes() } else this
    }
}
