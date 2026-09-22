package com.cereal.client.infrastructure.data.datasource.filesystem.security

import com.cereal.client.application.Environment
import com.cereal.client.application.exception.CrashReporter
import com.cereal_automation.cereal_client.BuildConfig
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key material used by [Encryption].
 *
 * Carries two derivations so that ciphertext written by the old code path keeps working while
 * all new ciphertext uses a full-entropy key:
 *
 * @property keyBytes Strong key material — the raw SHA-256 digest (or a PBKDF2-stretched value
 *   for sizes larger than the digest). Used for all AES/GCM encryption and decryption. Every
 *   byte carries the full 8 bits of entropy.
 * @property legacyKeyBytes ASCII bytes of the old decimal-stringified digest. Used *only* to
 *   decrypt pre-existing AES/CBC blobs during transparent migration. Never used for new writes.
 */
class EncryptionKey internal constructor(
    internal val keyBytes: ByteArray,
    internal val legacyKeyBytes: ByteArray,
)

/**
 * Encryption helper to read from and write to an encrypted stream. When the application runs in the local environment
 * the data is expected to be unencrypted.
 *
 * ## Ciphertext format
 *
 * New ciphertext (GCM, authenticated):
 *   [GCM_MAGIC (4 bytes)] [IV (12 bytes)] [GCM ciphertext + 16-byte auth tag]
 *
 * Legacy ciphertext (CBC, unauthenticated — read-only migration path):
 *   [IV (16 bytes)] [CBC ciphertext]
 *
 * All new writes use GCM with the full-entropy [EncryptionKey.keyBytes]. Two classes of
 * pre-existing ciphertext are decrypted transparently so user data survives the upgrade:
 * legacy CBC blobs (no magic prefix), and legacy GCM blobs written before the key-derivation
 * fix (#488) that carry the magic but were keyed with the old low-entropy ASCII bytes. Both
 * fall back to [EncryptionKey.legacyKeyBytes]; on the next write the value is re-encrypted
 * with the strong GCM key.
 */
class Encryption {
    companion object {
        private const val ALGORITHM = "AES"
        private const val CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val CBC_IV_SIZE = 16
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val MAX_ENTRY_SIZE = 50 * 1024 * 1024 // 50MB max per entry
        private const val READ_BUFFER_SIZE = 8 * 1024
        private const val SHA_256 = "SHA-256"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val BITS_PER_BYTE = 8
        private const val GCM_MAGIC_LAST_INDEX = 3

        // Fixed salt for stretching keys past the SHA-256 digest length. A static salt is
        // acceptable here because the input key material is itself secret and high-entropy,
        // and derivation must stay deterministic to decrypt persisted data across runs.
        private val PBKDF2_SALT = MessageDigest.getInstance(SHA_256).digest("cereal-client-kdf-v1".toByteArray())

        /**
         * Magic prefix that marks ciphertext produced by the authenticated GCM path.
         * Four bytes chosen to be invalid UTF-8 and not a realistic CBC IV prefix.
         * 0x00 0x47 0x43 0x4D = NUL 'G' 'C' 'M'
         */
        private val GCM_MAGIC = byteArrayOf(0x00, 0x47, 0x43, 0x4D)

        private val logger = LoggerFactory.getLogger(Encryption::class.java)

        /**
         * Derives the AES [EncryptionKey] for the given inputs.
         *
         * The strong key is the raw SHA-256 digest of the key material (full 8 bits of entropy
         * per byte). When [size] exceeds the 32-byte digest, the key is stretched with PBKDF2
         * rather than zero-padded. The returned object also carries the old low-entropy key so
         * that legacy AES/CBC ciphertext can still be read during transparent migration.
         */
        fun getEncryptionKey(
            appKey: String,
            userKey: String? = null,
            size: Int = 32,
        ): EncryptionKey =
            EncryptionKey(
                keyBytes = deriveStrongKey(appKey + userKey.orEmpty(), size),
                legacyKeyBytes = deriveLegacyKey(appKey + userKey, size),
            )

        private fun deriveStrongKey(
            material: String,
            size: Int,
        ): ByteArray {
            val digest = MessageDigest.getInstance(SHA_256).digest(material.toByteArray(Charsets.UTF_8))
            return if (size <= digest.size) {
                digest.copyOf(size)
            } else {
                val spec = PBEKeySpec(material.toCharArray(), PBKDF2_SALT, PBKDF2_ITERATIONS, size * BITS_PER_BYTE)
                SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            }
        }

        /**
         * Reproduces the old broken derivation (decimal stringification of the digest, taken as
         * ASCII bytes). Kept solely so legacy CBC blobs encrypted with this key still decrypt.
         */
        private fun deriveLegacyKey(
            material: String,
            size: Int,
        ): ByteArray {
            val digest = MessageDigest.getInstance(SHA_256).digest(material.toByteArray())
            return digest.joinToString("").substring(0, size).toByteArray()
        }

        private fun createSecretKey(keyBytes: ByteArray): Key = SecretKeySpec(keyBytes, ALGORITHM)

        /**
         * Reads at most [limit] bytes from this stream, throwing if the stream produces more.
         *
         * [java.util.jar.JarEntry.size] reports the *declared* uncompressed size, which a crafted
         * jar can lie about. Enforcing the cap on the bytes actually read means a malicious entry
         * cannot exhaust memory by streaming past its declared size (the classic zip-bomb shape).
         */
        private fun InputStream.readAtMost(limit: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) {
                    throw IOException("Jar entry exceeded maximum allowed size of $limit bytes")
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        private fun hasgcmMagic(data: ByteArray): Boolean =
            data.size >= GCM_MAGIC.size &&
                data[0] == GCM_MAGIC[0] &&
                data[1] == GCM_MAGIC[1] &&
                data[2] == GCM_MAGIC[2] &&
                data[GCM_MAGIC_LAST_INDEX] == GCM_MAGIC[GCM_MAGIC_LAST_INDEX]

        /**
         * Encrypts a byte array using AES/GCM/NoPadding (authenticated encryption).
         *
         * Format: [GCM_MAGIC (4)] [IV (12)] [ciphertext + 16-byte auth tag]
         */
        fun encryptBytes(
            data: ByteArray,
            key: EncryptionKey,
        ): ByteArray {
            val iv = ByteArray(GCM_IV_SIZE)
            SecureRandom().nextBytes(iv)
            val secretKey = createSecretKey(key.keyBytes)
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val ciphertext = cipher.doFinal(data)
            return GCM_MAGIC + iv + ciphertext
        }

        /**
         * Decrypts a byte array produced by [encryptBytes].
         *
         * Detects the format by the [GCM_MAGIC] prefix:
         * - Present  → AES/GCM (authenticated; tamper detected, exception thrown)
         * - Absent   → legacy AES/CBC migration path (read-only; no authentication)
         *
         * @throws javax.crypto.AEADBadTagException if GCM authentication fails (tampered ciphertext)
         * @throws javax.crypto.BadPaddingException / IllegalArgumentException for malformed legacy CBC data
         */
        fun decryptBytes(
            encryptedData: ByteArray,
            key: EncryptionKey,
        ): ByteArray =
            if (hasgcmMagic(encryptedData)) {
                decryptGcm(encryptedData, key)
            } else {
                decryptCbcLegacy(encryptedData, key.legacyKeyBytes)
            }

        /**
         * Decrypts GCM ciphertext, trying the strong key first and falling back to the legacy
         * ASCII key.
         *
         * GCM was in use before the key-derivation fix (#488), so ciphertext written by those
         * builds carries the GCM magic but was encrypted with the old low-entropy ASCII key.
         * Such a blob fails authentication under [EncryptionKey.keyBytes] with an
         * [AEADBadTagException]; we then retry with [EncryptionKey.legacyKeyBytes] so the value
         * survives the upgrade (it is re-encrypted with the strong key on the next write).
         *
         * Only the tag-mismatch case falls back — a genuine wrong key (e.g. a release DB opened
         * by a differently keyed build) still fails under both and the exception propagates.
         */
        private fun decryptGcm(
            encryptedData: ByteArray,
            key: EncryptionKey,
        ): ByteArray =
            try {
                decryptGcm(encryptedData, key.keyBytes)
            } catch (e: AEADBadTagException) {
                logger.warn("GCM decryption failed under the strong key; retrying with the legacy key", e)
                decryptGcm(encryptedData, key.legacyKeyBytes)
            }

        private fun decryptGcm(
            encryptedData: ByteArray,
            keyBytes: ByteArray,
        ): ByteArray {
            val iv = encryptedData.sliceArray(GCM_MAGIC.size until GCM_MAGIC.size + GCM_IV_SIZE)
            val ciphertext = encryptedData.sliceArray(GCM_MAGIC.size + GCM_IV_SIZE until encryptedData.size)
            val secretKey = createSecretKey(keyBytes)
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            return cipher.doFinal(ciphertext)
        }

        /** Legacy CBC decryption — used only to migrate existing data. */
        private fun decryptCbcLegacy(
            encryptedData: ByteArray,
            keyBytes: ByteArray,
        ): ByteArray {
            val iv = encryptedData.sliceArray(0 until CBC_IV_SIZE)
            val actualEncryptedData = encryptedData.sliceArray(CBC_IV_SIZE until encryptedData.size)
            val secretKey = createSecretKey(keyBytes)
            val cipher = Cipher.getInstance(CBC_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            return cipher.doFinal(actualEncryptedData)
        }
    }

    /**
     * Encrypts each entry in a jar file individually and writes to output stream
     */
    fun writeJar(
        inputStream: InputStream,
        outputStream: OutputStream,
        key: EncryptionKey,
    ) {
        // Keep this check inline to make obfuscation (hopefully) remove it.
        if (BuildConfig.ENVIRONMENT == Environment.LOCAL) {
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            JarInputStream(inputStream).use { jarInput ->
                JarOutputStream(outputStream).use { jarOutput ->
                    var entry: JarEntry?
                    while (jarInput.nextJarEntry.also { entry = it } != null) {
                        writeEncryptedEntry(entry!!, jarInput, jarOutput, key)
                    }
                }
            }
        }
    }

    private fun writeEncryptedEntry(
        currentEntry: JarEntry,
        jarInput: JarInputStream,
        jarOutput: JarOutputStream,
        key: EncryptionKey,
    ) {
        // Create new jar entry with same name
        val newEntry = JarEntry(currentEntry.name)
        jarOutput.putNextEntry(newEntry)

        if (!currentEntry.isDirectory) {
            // Read entry data, capping on the bytes actually read (declared size is untrusted).
            val entryData = jarInput.readAtMost(MAX_ENTRY_SIZE.toLong())

            // Encrypt entry data
            val encryptedData = encryptBytes(entryData, key)
            jarOutput.write(encryptedData)
        }

        jarOutput.closeEntry()
    }

    /**
     * Reads a specific entry from a jar with encrypted entries
     */
    fun readJarEntry(
        jarFile: File,
        entryName: String,
        encryptionKey: EncryptionKey,
    ): ByteArray? {
        return try {
            JarFile(jarFile).use { jar ->
                val entry = jar.getJarEntry(entryName)
                if (entry != null && !entry.isDirectory) {
                    // Cheap early reject on the declared size. This is advisory only — the value can
                    // be lied about — so the read below independently enforces the cap on real bytes.
                    if (entry.size > MAX_ENTRY_SIZE) {
                        return null
                    }
                    val data = jar.getInputStream(entry).readAtMost(MAX_ENTRY_SIZE.toLong())
                    // Keep this check inline to make obfuscation (hopefully) remove it.
                    if (BuildConfig.ENVIRONMENT == Environment.LOCAL) {
                        data
                    } else {
                        decryptBytes(data, encryptionKey)
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            val message = "Failed to read jar entry: $entryName"
            logger.error(message, e)
            CrashReporter.report(e, mapOf("entry" to entryName, "jar" to jarFile.name))
            null
        }
    }
}
