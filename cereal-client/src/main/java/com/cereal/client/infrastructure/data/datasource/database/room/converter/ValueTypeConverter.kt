package com.cereal.client.infrastructure.data.datasource.database.room.converter

import androidx.room.TypeConverter
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType

/**
 * Room type converter for [ValueType], replacing Room's generated enum mapping so that a stored label
 * the enum no longer declares reads as [ValueType.UNKNOWN] instead of throwing.
 *
 * This exists because a value type can be retired. `STRING_LIST` was, when `List<String>` stopped
 * being a configuration return type — but the 12 → 13 migration deliberately leaves `script_parameter`
 * rows alone, since a script writes its own state there. Room's generated mapping ends in
 * `throw IllegalArgumentException("Can't convert value to enum, unknown value: …")`, so one such
 * leftover row would take down every read of that table rather than degrading to "no value".
 *
 * Unknown is the right reading, not a fallback: the client has no way to interpret a value whose type
 * it no longer knows, and [ValueType.UNKNOWN] is already the label that means exactly that — the
 * key-value mapper's `UNKNOWN` branch resolves it to `null`.
 */
class ValueTypeConverter {
    @TypeConverter
    fun fromValueType(valueType: ValueType?): String? = valueType?.name

    @TypeConverter
    fun toValueType(name: String?): ValueType? = name?.let { stored -> ValueType.entries.firstOrNull { it.name == stored } ?: ValueType.UNKNOWN }
}
