package com.cereal.client.infrastructure.data.datasource.database.room.converter

import com.cereal.client.application.Environment
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import com.cereal_automation.cereal_client.BuildConfig
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Base class for EncryptedString converters that provides shared encryption/decryption logic.
 *
 * This abstract class contains the common encryption and decryption methods that are used
 * by both ApplicationEncryptedStringConverter and UserEncryptedStringConverter.
 */
abstract class BaseEncryptedStringConverter {
    private val logger = LoggerFactory.getLogger(BaseEncryptedStringConverter::class.java)

    /**
     * Get the encryption key to use for field-level encryption.
     * Subclasses must implement this to provide the appropriate key strategy.
     */
    protected abstract fun getEncryptionKey(): EncryptionKey

    protected fun encryptString(
        plaintext: String,
        key: EncryptionKey,
    ): String {
        // In local environment, don't encrypt for easier debugging
        if (BuildConfig.ENVIRONMENT == Environment.LOCAL) {
            return plaintext
        }

        val encryptedBytes = Encryption.encryptBytes(plaintext.toByteArray(), key)
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    /**
     * Decrypts an encrypted string field value.
     *
     * Returns `null` on decryption failure so that the Room entity field is null rather
     * than propagating ciphertext or garbage into the application. Callers that map the
     * entity to a domain type should treat a null result as a data-integrity error and
     * handle it (e.g. log, skip, or surface to the user) rather than proceeding silently
     * with corrupted data.
     *
     * A decryption failure on a GCM-encrypted value means the ciphertext was tampered
     * with or the wrong key was used. A failure on a legacy CBC value means the data is
     * malformed or was never encrypted in the first place.
     */
    protected fun decryptString(
        encryptedText: String,
        key: EncryptionKey,
    ): String? {
        // In local environment, return as-is
        if (BuildConfig.ENVIRONMENT == Environment.LOCAL) {
            return encryptedText
        }

        return try {
            val encryptedBytes = Base64.getDecoder().decode(encryptedText)
            val decryptedBytes = Encryption.decryptBytes(encryptedBytes, key)
            String(decryptedBytes)
        } catch (e: Exception) {
            logger.error("Decryption failed for a database field — the value will be treated as null", e)
            CrashReporter.report(e)
            null
        }
    }

    protected fun convertFromEncryptedString(encryptedString: EncryptedString?): String? =
        encryptedString?.value?.let { plaintext ->
            encryptString(plaintext, getEncryptionKey())
        }

    protected fun convertToEncryptedString(encryptedText: String?): EncryptedString? =
        encryptedText?.let { encrypted ->
            val decrypted = decryptString(encrypted, getEncryptionKey()) ?: return null
            EncryptedString(decrypted)
        }
}
