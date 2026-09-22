package com.cereal.client.infrastructure.data.datasource.database.room.converter

import androidx.room.TypeConverter
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Room type converter for EncryptedString used in the application database.
 *
 * This converter uses only the application encryption key (no user key) to encrypt/decrypt
 * sensitive data in the application database. This is appropriate for application-level
 * settings that are not user-specific.
 *
 * The encryption is transparent to the application logic - any field of type EncryptedString
 * will be automatically encrypted when stored and decrypted when retrieved.
 */
class ApplicationEncryptedStringConverter :
    BaseEncryptedStringConverter(),
    KoinComponent {
    /**
     * Get the encryption key for application database field-level encryption.
     * Uses only the application database key without any user-specific component.
     */
    override fun getEncryptionKey(): EncryptionKey = get<RoomDatabases>().applicationEncryptionKey

    @TypeConverter
    fun fromEncryptedString(encryptedString: EncryptedString?): String? = convertFromEncryptedString(encryptedString)

    @TypeConverter
    fun toEncryptedString(encryptedText: String?): EncryptedString? = convertToEncryptedString(encryptedText)
}
