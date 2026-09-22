package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.datasets.Group
import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.script.configuration.itemForKey
import com.cereal.client.domain.model.script.configuration.parseValue
import com.cereal.client.domain.model.script.configuration.toConfigValue
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptParameterEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.error.MissingPropertyException
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room mapper for converting between key-value parameters and domain objects
 */
@OptIn(ExperimentalTime::class)
class KeyValueRoomMapper(
    private val proxyDataSource: ProxyDataSource,
    private val datasetDataSource: DatasetDataSource?,
) {
    private companion object {
        /** A list on the wire: an array of objects whose values are all strings. */
        private val LIST_SERIALIZER = ListSerializer(MapSerializer(String.serializer(), String.serializer()))
    }

    private val logger = LoggerFactory.getLogger(KeyValueRoomMapper::class.java)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * Coerces a value stored as text into a [Secret] when the *current* definition declares the item
     * at [key] as a secret.
     *
     * This is the `String` → `Secret` migration path, and it is the difference between a developer
     * being able to adopt the type and not. Value retrieval dispatches on the *stored* type, and a
     * value whose type does not match the item's declared type degrades to no value at all — so
     * without this, changing a shipped item's return type from `String` to `Secret` would silently
     * blank the credential of every existing user.
     *
     * Deliberately **one-directional**: there is no counterpart coercing a stored `SECRET` down to a
     * string for a text definition. A downgrade would take a value the user was told is protected and
     * start printing it in the task list. A stored secret against a text definition therefore keeps
     * the existing mismatch behaviour (no value) rather than being un-masked.
     *
     * No ciphertext is touched. The value column is an `EncryptedString` either way, so this relabels
     * an already-encrypted value on read; the next save writes it back under [ValueType.SECRET]
     * because [getValueType] labels a [Secret] as such. This is not a data migration (see ADR-0001).
     */
    private fun String?.coercedToSecretIfDeclared(
        key: String,
        definition: ScriptConfigurationDefinition?,
    ): Any? =
        if (definition?.itemForKey(key)?.type is ConfigItemType.SecretConfigItem) {
            this?.let { Secret(it) }
        } else {
            this
        }

    suspend fun mapParametersFromEntities(
        parameters: List<ScriptParameterEntity>,
        user: User,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): Map<String, Any> =
        parameters
            .mapNotNull { parameter ->
                val value = parameter.getValue(user, scriptConfigurationDefinition)
                if (value != null) {
                    parameter.key to value
                } else {
                    null
                }
            }.toMap()

    suspend fun mapConfigurationItemsFromEntities(
        configurationItems: List<ScriptConfigurationItemEntity>,
        user: User,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): ScriptConfigurationValues =
        configurationItems
            .mapNotNull { item ->
                val value = item.getValue(user, scriptConfigurationDefinition)
                if (value != null) {
                    item.key to value.toConfigValue()
                } else {
                    null
                }
            }.toMap()

    // Exhaustive when over every ValueType inherently raises complexity; splitting would obscure the mapping.
    @Suppress("CyclomaticComplexMethod")
    private suspend fun ScriptParameterEntity.getValue(
        user: User,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): Any? {
        val stringValue = this.value?.value
        return when (this.type) {
            ValueType.BOOLEAN -> {
                stringValue?.toBoolean()
            }

            ValueType.INT -> {
                stringValue?.toInt()
            }

            ValueType.FLOAT -> {
                stringValue?.toFloat()
            }

            ValueType.DOUBLE -> {
                stringValue?.toDouble()
            }

            ValueType.STRING -> {
                stringValue.coercedToSecretIfDeclared(this.key, scriptConfigurationDefinition)
            }

            ValueType.SECRET -> {
                stringValue?.let { Secret(it) }
            }

            ValueType.PROXY -> {
                proxyDataSource.getProxy(user, stringValue!!)
            }

            ValueType.PROXY_GROUP -> {
                proxyDataSource.getProxyGroup(user, stringValue!!)
            }

            ValueType.FILE -> {
                File(stringValue!!)
            }

            ValueType.ENUM -> {
                val enumType = (scriptConfigurationDefinition?.itemForKey(this.key)?.type as? ConfigItemType.EnumConfigItem)?.enumType
                enumType?.java?.enumConstants?.firstOrNull { enumConstant -> enumConstant.name == stringValue }
            }

            ValueType.CUSTOM_DATASET_GROUP -> {
                datasetDataSource?.getDatasetGroup(user, stringValue!!)
                    ?: throw MissingPropertyException("Missing dataset datasource")
            }

            ValueType.CUSTOM_DATASET -> {
                datasetDataSource?.getDataset(user, stringValue!!)
                    ?: throw MissingPropertyException("Missing dataset datasource")
            }

            ValueType.UNKNOWN -> {
                null
            }

            ValueType.LONG -> {
                stringValue?.toLong()
            }

            ValueType.SHORT -> {
                stringValue?.toShort()
            }

            ValueType.INSTANT -> {
                stringValue?.toLong()?.let { Instant.fromEpochMilliseconds(it) }
            }

            ValueType.LIST -> {
                decodeList(stringValue, this.key, scriptConfigurationDefinition)
            }
        }
    }

    // Exhaustive when over every ValueType inherently raises complexity; splitting would obscure the mapping.
    @Suppress("CyclomaticComplexMethod")
    private suspend fun ScriptConfigurationItemEntity.getValue(
        user: User,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): Any? {
        val stringValue = this.value?.value
        return when (this.type) {
            ValueType.BOOLEAN -> {
                stringValue?.toBoolean()
            }

            ValueType.INT -> {
                stringValue?.toInt()
            }

            ValueType.FLOAT -> {
                stringValue?.toFloat()
            }

            ValueType.DOUBLE -> {
                stringValue?.toDouble()
            }

            ValueType.STRING -> {
                stringValue.coercedToSecretIfDeclared(this.key, scriptConfigurationDefinition)
            }

            ValueType.SECRET -> {
                stringValue?.let { Secret(it) }
            }

            ValueType.PROXY -> {
                proxyDataSource.getProxy(user, stringValue!!)
            }

            ValueType.PROXY_GROUP -> {
                proxyDataSource.getProxyGroup(user, stringValue!!)
            }

            ValueType.FILE -> {
                File(stringValue!!)
            }

            ValueType.ENUM -> {
                val enumType = (scriptConfigurationDefinition?.itemForKey(this.key)?.type as? ConfigItemType.EnumConfigItem)?.enumType
                enumType?.java?.enumConstants?.firstOrNull { enumConstant -> enumConstant.name == stringValue }
            }

            ValueType.CUSTOM_DATASET_GROUP -> {
                datasetDataSource?.getDatasetGroup(user, stringValue!!)
                    ?: throw MissingPropertyException("Missing dataset datasource")
            }

            ValueType.CUSTOM_DATASET -> {
                datasetDataSource?.getDataset(user, stringValue!!)
                    ?: throw MissingPropertyException("Missing dataset datasource")
            }

            ValueType.UNKNOWN -> {
                null
            }

            ValueType.LONG -> {
                stringValue?.toLong()
            }

            ValueType.SHORT -> {
                stringValue?.toShort()
            }

            ValueType.INSTANT -> {
                stringValue?.toLong()?.let { Instant.fromEpochMilliseconds(it) }
            }

            ValueType.LIST -> {
                decodeList(stringValue, this.key, scriptConfigurationDefinition)
            }
        }
    }

    suspend fun mapTaskConfigurationFromEntities(
        configurationEntities: List<TaskConfigurationEntity>,
        user: User,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): ScriptConfigurationValues =
        configurationEntities
            .mapNotNull { entity ->
                val value = entity.getValue(user, scriptConfigurationDefinition)
                if (value != null) {
                    entity.key to value.toConfigValue()
                } else {
                    null
                }
            }.toMap()

    // Exhaustive when over every ValueType inherently raises complexity; splitting would obscure the mapping.
    @Suppress("CyclomaticComplexMethod")
    private suspend fun TaskConfigurationEntity.getValue(
        user: User,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): Any? {
        val stringValue = this.value?.value
        return when (this.type) {
            ValueType.BOOLEAN -> {
                stringValue?.toBoolean()
            }

            ValueType.INT -> {
                stringValue?.toInt()
            }

            ValueType.FLOAT -> {
                stringValue?.toFloat()
            }

            ValueType.DOUBLE -> {
                stringValue?.toDouble()
            }

            ValueType.STRING -> {
                stringValue.coercedToSecretIfDeclared(this.key, scriptConfigurationDefinition)
            }

            ValueType.SECRET -> {
                stringValue?.let { Secret(it) }
            }

            ValueType.PROXY -> {
                proxyDataSource.getProxy(user, stringValue!!)
            }

            ValueType.PROXY_GROUP -> {
                proxyDataSource.getProxyGroup(user, stringValue!!)
            }

            ValueType.FILE -> {
                File(stringValue!!)
            }

            ValueType.ENUM -> {
                val enumType = (scriptConfigurationDefinition?.itemForKey(this.key)?.type as? ConfigItemType.EnumConfigItem)?.enumType
                enumType?.java?.enumConstants?.firstOrNull { enumConstant -> enumConstant.name == stringValue }
            }

            ValueType.CUSTOM_DATASET_GROUP -> {
                datasetDataSource?.getDatasetGroup(user, stringValue!!)
                    ?: throw MissingPropertyException("Missing dataset datasource")
            }

            ValueType.CUSTOM_DATASET -> {
                datasetDataSource?.getDataset(user, stringValue!!)
                    ?: throw MissingPropertyException("Missing dataset datasource")
            }

            ValueType.UNKNOWN -> {
                null
            }

            ValueType.LONG -> {
                stringValue?.toLong()
            }

            ValueType.SHORT -> {
                stringValue?.toShort()
            }

            ValueType.INSTANT -> {
                stringValue?.toLong()?.let { Instant.fromEpochMilliseconds(it) }
            }

            ValueType.LIST -> {
                decodeList(stringValue, this.key, scriptConfigurationDefinition)
            }
        }
    }

    /**
     * Decodes the stored rows of a list item.
     *
     * Schema drift is tolerated per field: a stored key the record no longer declares is dropped, and a
     * newly declared key is treated as unset. Rows therefore survive a script update, and ordinary
     * validation then requires the user to fill any new non-nullable field before starting.
     */
    private fun decodeList(
        stringValue: String?,
        key: String,
        scriptConfigurationDefinition: ScriptConfigurationDefinition?,
    ): ListRows? {
        val storedRows =
            stringValue?.let {
                try {
                    json.decodeFromString(LIST_SERIALIZER, it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A malformed stored value silently resets to null; report so the underlying
                    // corruption / serialization defect is visible instead of being masked.
                    logger.error("Failed to deserialize stored LIST value; treating as null", e)
                    CrashReporter.report(e)
                    null
                }
            } ?: return null

        val fieldDefinitions =
            (scriptConfigurationDefinition?.itemForKey(key)?.type as? ConfigItemType.ListConfigItem)
                ?.items
                ?: run {
                    // Without the record's shape the stored strings cannot be typed. This happens when the
                    // configuration definition is unavailable (e.g. the script is no longer installed).
                    logger.warn("No list definition found for key '$key'; treating stored rows as null")
                    return null
                }

        return ListRows(
            storedRows.map { storedRow ->
                ListRow(
                    fieldDefinitions
                        .mapNotNull { fieldDefinition ->
                            val storedField = storedRow[fieldDefinition.key] ?: return@mapNotNull null
                            fieldDefinition.parseFieldValue(storedField, key)?.let { fieldDefinition.key to it }
                        }.toMap(),
                )
            },
        )
    }

    /**
     * Parses one stored field of a list row. A value the field's current type can no longer parse
     * — the script author changed the field's type — is dropped so the rest of the row survives; ordinary
     * validation then asks the user to supply it again.
     */
    private fun ScriptConfigurationItemDefinition.parseFieldValue(
        storedValue: String,
        listItemKey: String,
    ): ConfigValue? =
        try {
            parseValue(storedValue)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(
                "Stored value for field '$key' of list '$listItemKey' does not parse as $type; treating as unset",
                e,
            )
            null
        }

    // Exhaustive when over every supported value type inherently raises complexity; splitting would obscure the mapping.
    @Suppress("CyclomaticComplexMethod")
    fun valueToString(value: Any?): String? =
        when (value) {
            null -> {
                null
            }

            is String -> {
                value
            }

            // The plaintext is what gets stored, exactly as for a string: the value column is an
            // EncryptedString regardless of type, so encryption is unchanged (see ADR-0001).
            is Secret -> {
                value.reveal()
            }

            is Boolean -> {
                value.toString()
            }

            is Int -> {
                value.toString()
            }

            is Long -> {
                value.toString()
            }

            is Float -> {
                value.toString()
            }

            is Double -> {
                value.toString()
            }

            is Short -> {
                value.toString()
            }

            is Instant -> {
                value.toEpochMilliseconds().toString()
            }

            is File -> {
                value.absolutePath
            }

            is Enum<*> -> {
                value.name
            }

            is Group<*> -> {
                value.id
            }

            is Proxy -> {
                value.id.toString()
            }

            is CustomDatasetItem -> {
                value.id.toString()
            }

            is ListRows -> {
                // A JSON array of objects, one per row, with every field value stored as a string; the
                // definition's raw-value parser types them again on the way back in.
                json.encodeToString(
                    LIST_SERIALIZER,
                    value.rows.map { row ->
                        row.fields.mapNotNull { (key, fieldValue) -> valueToString(fieldValue.raw)?.let { key to it } }.toMap()
                    },
                )
            }

            else -> {
                throw UnsupportedConfigurationTypeException("Unsupported value type: '${value.javaClass}'.")
            }
        }

    /**
     * The stored type label for [value].
     *
     * [Secret] is matched ahead of [String] deliberately: the two are unrelated types today, but were
     * `Secret` ever to become a `CharSequence`, falling through to `STRING` would silently strip a
     * credential's masking on the next read.
     *
     * The exhaustive `when` over every supported value type inherently raises complexity; splitting it
     * would obscure the mapping.
     */
    @Suppress("CyclomaticComplexMethod")
    fun getValueType(value: Any?): ValueType =
        when (value) {
            null -> ValueType.UNKNOWN
            is Secret -> ValueType.SECRET
            is String -> ValueType.STRING
            is Boolean -> ValueType.BOOLEAN
            is Int -> ValueType.INT
            is Long -> ValueType.LONG
            is Float -> ValueType.FLOAT
            is Double -> ValueType.DOUBLE
            is Short -> ValueType.SHORT
            is Instant -> ValueType.INSTANT
            is File -> ValueType.FILE
            is Enum<*> -> ValueType.ENUM
            is ProxyGroup -> ValueType.PROXY_GROUP
            is Proxy -> ValueType.PROXY
            is CustomDatasetGroup -> ValueType.CUSTOM_DATASET_GROUP
            is CustomDatasetItem -> ValueType.CUSTOM_DATASET
            is ListRows -> ValueType.LIST
            else -> throw UnsupportedConfigurationTypeException("Unsupported value type: '${value.javaClass}'.")
        }
}
