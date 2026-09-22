package com.cereal.client.infrastructure.data.datasource.database.room.converter

import androidx.room.TypeConverter
import java.util.UUID

/**
 * Room type converter for UUID
 */
class UuidConverter {
    @TypeConverter
    fun fromUuid(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUuid(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }
}
