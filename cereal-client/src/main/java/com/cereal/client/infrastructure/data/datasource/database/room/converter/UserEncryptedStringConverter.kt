package com.cereal.client.infrastructure.data.datasource.database.room.converter

import androidx.room.TypeConverter
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import com.cereal.client.infrastructure.di.UserScopeProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named

/**
 * Room type converter for EncryptedString used in user databases.
 *
 * This converter uses the authenticated user's encryption key to encrypt/decrypt sensitive
 * data in user-specific databases. This provides enhanced security by making encrypted data
 * unique to each user.
 *
 * The encryption is transparent to the application logic - any field of type EncryptedString
 * will be automatically encrypted when stored and decrypted when retrieved.
 *
 * Falls back to application-level encryption if no user is authenticated.
 */
class UserEncryptedStringConverter :
    BaseEncryptedStringConverter(),
    KoinComponent {
    /**
     * Get the encryption key for user database field-level encryption.
     * Uses the current user's encryption key if available, otherwise falls back to application key.
     */
    override fun getEncryptionKey(): EncryptionKey =
        get<UserScopeProvider>().currentScope?.get(named("UserEncryptionKey"))
            ?: throw RuntimeException("No user database is currently open.")

    @TypeConverter
    fun fromEncryptedString(encryptedString: EncryptedString?): String? = convertFromEncryptedString(encryptedString)

    @TypeConverter
    fun toEncryptedString(encryptedText: String?): EncryptedString? = convertToEncryptedString(encryptedText)
}
