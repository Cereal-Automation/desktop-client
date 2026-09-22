package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.datasets.Group
import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.sdk.statemodifier.StateModifier
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * @param multiTask true if the configuration item is a group with multiple records that can be used for multiple tasks.
 */
sealed class ConfigItemType(
    val scriptValueType: KClass<*>,
    val taskValueType: KClass<*> = scriptValueType,
) {
    data object BooleanConfigItem : ConfigItemType(Boolean::class)

    data object StringConfigItem : ConfigItemType(String::class)

    /**
     * A user-supplied credential. Behaves like [StringConfigItem] everywhere except display: the value
     * is carried as a [Secret] so it renders masked instead of in clear text.
     */
    data object SecretConfigItem : ConfigItemType(Secret::class)

    data object IntConfigItem : ConfigItemType(Int::class)

    data object FloatConfigItem : ConfigItemType(Float::class)

    data object DoubleConfigItem : ConfigItemType(Double::class)

    /**
     * A list: a configuration item returning `List<T>` where `T` is a script-defined
     * `ScriptConfigurationListItem` interface. This is the only list shape a configuration may declare.
     *
     * @param itemType the record interface itself; the running script is handed one dynamic proxy of
     *   this type per row.
     * @param items the record's field definitions — the single source of truth the form, validation and
     *   persistence all read the record's shape from.
     */
    data class ListConfigItem(
        val itemType: KClass<*>,
        val items: List<ScriptConfigurationItemDefinition>,
    ) : ConfigItemType(List::class)

    data class EnumConfigItem(
        val enumType: KClass<Enum<*>>,
    ) : ConfigItemType(enumType)

    data object ProxyConfigItem : ConfigItemType(ProxyGroup::class, Proxy::class)

    data object ProxyGroupConfigItem : ConfigItemType(ProxyGroup::class, ProxyGroup::class)

    data class GroupedConfigItem(
        val items: List<ScriptConfigurationItemDefinition>,
    ) : ConfigItemType(CustomDatasetGroup::class, CustomDatasetItem::class)
}

/**
 * Whether this config item type represents a group of records (proxy or custom dataset). This is the
 * single type-level source of truth for "is this a group?" — prefer it over enumerating the grouped
 * variants at each call site. The value-level counterpart, used when a concrete value is available,
 * is [com.cereal.client.domain.model.script.configuration.isGroup] on `ConfigValue`.
 */
val ConfigItemType.isGroup: Boolean
    get() =
        this is ConfigItemType.GroupedConfigItem ||
            this is ConfigItemType.ProxyConfigItem ||
            this is ConfigItemType.ProxyGroupConfigItem

data class ScriptConfigurationItemDefinition(
    val name: String,
    val description: String,
    val key: ConfigKey,
    val position: Int,
    val type: ConfigItemType,
    val isNullable: Boolean,
    val stateModifier: StateModifier?,
    val isScriptIdentifier: Boolean,
    val defaultValue: ConfigValue? = null,
)

fun ScriptConfigurationItemDefinition.containsValidData(value: ConfigValue): Boolean =
    when {
        // Group-backed types (proxy/dataset): the configured value carries the group, so a non-empty
        // group is valid. Single-record values (a picked proxy/item) have no group and count as valid.
        type.isGroup -> (value.asGroup?.numberOfItems ?: 1) > 0

        type is ConfigItemType.StringConfigItem -> (value as? ConfigValue.StringValue)?.raw?.isNotEmpty() == true

        // Same rule as a string: present but empty is not valid data. Checked on the revealed value
        // because emptiness is a property of the credential, not of its masked rendering.
        type is ConfigItemType.SecretConfigItem -> (value as? ConfigValue.SecretValue)?.raw?.reveal()?.isNotEmpty() == true

        type is ConfigItemType.ListConfigItem -> type.containsCompleteRows(value)

        else -> true
    }

/**
 * A list holds valid data when it has at least one row and every row supplies a value for every
 * non-nullable field the record declares.
 *
 * Incomplete rows are reported rather than dropped: dropping them would lose typed data the user entered
 * and paper over the failure the invocation handler raises for a missing non-nullable value.
 */
private fun ConfigItemType.ListConfigItem.containsCompleteRows(value: ConfigValue): Boolean {
    val rows = (value as? ConfigValue.ListValue)?.raw?.rows ?: return false
    return rows.isNotEmpty() && rows.all { row -> items.all { it.isNullable || row.fields[it.key] != null } }
}

fun ScriptConfigurationItemDefinition.isValidReturnType(
    value: ConfigValue,
    isTaskConfiguration: Boolean = false,
): Boolean {
    val valueType = if (isTaskConfiguration) type.taskValueType else type.scriptValueType
    return when (type) {
        is ConfigItemType.ListConfigItem -> value is ConfigValue.ListValue
        else -> value.raw::class.isSubclassOf(valueType)
    }
}

inline fun <reified T : Any> ScriptConfigurationItemDefinition.getValue(values: ScriptConfigurationValues?): T? = values?.get(key)?.raw as? T

/** The spellings a boolean cell may use, case-insensitively. Nothing else is accepted. */
val BOOLEAN_TRUE_VALUES = listOf("true", "yes", "1")

/** @see BOOLEAN_TRUE_VALUES */
val BOOLEAN_FALSE_VALUES = listOf("false", "no", "0")

/** The names of this enum's constants, in declaration order — the values a CSV cell may hold. */
val ConfigItemType.EnumConfigItem.constantNames: List<String>
    get() = enumType.java.enumConstants.map { it.name }

/**
 * Converts a raw textual value (e.g. a CSV cell) into the typed [ConfigValue] that matches this
 * definition's [ConfigItemType]. Returns null when [rawValue] is null.
 *
 * Every rejection is loud. A boolean cell that is not one of [BOOLEAN_TRUE_VALUES] /
 * [BOOLEAN_FALSE_VALUES], and an enum cell matching no constant, both throw rather than resolving to
 * `false` or `null` — silently importing a value the file did not contain is a worse failure than
 * refusing the file.
 *
 * @throws IllegalArgumentException when [rawValue] cannot be converted to this definition's type.
 *   This covers [NumberFormatException] for the numeric types.
 * @throws UnsupportedConfigurationTypeException when the type cannot be imported from raw text
 *   (e.g. grouped, proxy or list types).
 */
fun ScriptConfigurationItemDefinition.parseValue(rawValue: String?): ConfigValue? =
    when (type) {
        ConfigItemType.IntConfigItem -> {
            rawValue?.trim()?.toInt()?.toConfigValue()
        }

        ConfigItemType.BooleanConfigItem -> {
            rawValue?.toStrictBoolean()?.toConfigValue()
        }

        ConfigItemType.FloatConfigItem -> {
            rawValue?.trim()?.toFloat()?.toConfigValue()
        }

        ConfigItemType.DoubleConfigItem -> {
            rawValue?.trim()?.toDouble()?.toConfigValue()
        }

        ConfigItemType.StringConfigItem -> {
            rawValue?.toConfigValue()
        }

        // A credential imported from a CSV cell. Wrapped immediately so the value is masked from the
        // moment it enters the domain — the dataset outlives the file it came from.
        ConfigItemType.SecretConfigItem -> {
            rawValue?.let { Secret(it).toConfigValue() }
        }

        is ConfigItemType.EnumConfigItem -> {
            rawValue?.let { value -> type.toEnumConstant(value) }?.toConfigValue()
        }

        else -> {
            throw UnsupportedConfigurationTypeException("Unsupported config type: '$type'.")
        }
    }

private fun String.toStrictBoolean(): Boolean =
    when (trim().lowercase()) {
        in BOOLEAN_TRUE_VALUES -> true
        in BOOLEAN_FALSE_VALUES -> false
        else -> throw IllegalArgumentException(ConfigItemType.BooleanConfigItem.describeRejection(this))
    }

private fun ConfigItemType.EnumConfigItem.toEnumConstant(value: String): Enum<*> =
    enumType.java.enumConstants.firstOrNull { it.name == value.trim() }
        ?: throw IllegalArgumentException(describeRejection(value))

/**
 * Why a raw value could not be converted to this type, phrased for the person holding the spreadsheet.
 * The single producer of that sentence: [parseValue] throws it, and the importers report it against the
 * row and column the value came from.
 */
internal fun ConfigItemType.describeRejection(rawValue: String): String =
    when (this) {
        ConfigItemType.IntConfigItem -> {
            "'$rawValue' is not a whole number."
        }

        ConfigItemType.FloatConfigItem, ConfigItemType.DoubleConfigItem -> {
            "'$rawValue' is not a number."
        }

        ConfigItemType.BooleanConfigItem -> {
            "'$rawValue' is not a yes/no value. Accepted: ${(BOOLEAN_TRUE_VALUES + BOOLEAN_FALSE_VALUES).joinToString(", ")}."
        }

        is ConfigItemType.EnumConfigItem -> {
            "'$rawValue' is not one of: ${constantNames.joinToString(", ")}."
        }

        else -> {
            "'$rawValue' is not a valid value."
        }
    }
