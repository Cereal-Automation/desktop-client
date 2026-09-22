package com.cereal.client.infrastructure.data.datasource.database.room.converter

import androidx.room.TypeConverter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room type converter for kotlin.time.Instant
 */
class DateTimeConverter {
    @OptIn(ExperimentalTime::class)
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @OptIn(ExperimentalTime::class)
    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let { Instant.fromEpochMilliseconds(it) }
}
