package com.cereal.client.infrastructure.data.datasource.database.room.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/**
 * Room type converter for JSON serialization
 */
class JsonConverter {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(jsonString: String?): List<String>? = jsonString?.let { json.decodeFromString<List<String>>(it) }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? = map?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringMap(jsonString: String?): Map<String, String>? = jsonString?.let { json.decodeFromString<Map<String, String>>(it) }
}
