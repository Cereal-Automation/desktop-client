package com.cereal.client.infrastructure.data.datasource.database.room.type

/**
 * A wrapper class for strings that should be encrypted when stored in the database.
 *
 * This class provides a type-safe way to handle encrypted strings, ensuring that
 * sensitive data is automatically encrypted/decrypted through Room TypeConverters.
 * Simply use this type instead of String for any field that should be encrypted.
 *
 * @property value The decrypted string value. This is the actual value that will be used
 *                 in the application logic.
 */
data class EncryptedString(
    val value: String?,
) {
    companion object {
        /**
         * Creates an EncryptedString from a nullable string value
         */
        fun from(value: String?): EncryptedString? = if (value != null) EncryptedString(value) else null

        /**
         * Creates an EncryptedString from a non-null string value
         */
        fun of(value: String): EncryptedString = EncryptedString(value)
    }

    /**
     * Returns the decrypted string value, or empty string if the value is null
     */
    fun getValueOrEmpty(): String = value ?: ""

    override fun toString(): String {
        return "EncryptedString(***)" // Don't expose the actual value in toString for security
    }
}
