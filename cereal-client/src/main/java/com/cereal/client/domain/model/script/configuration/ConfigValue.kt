@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.datasets.Group
import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Type-safe representation of a single script configuration value.
 *
 * Replaces the previously untyped `Any` used in [com.cereal.client.domain.model.script.ScriptConfigurationValues],
 * so consumers can match on the variant instead of relying on unchecked casts. The wrapped value is exposed
 * through [raw] for the few boundaries that genuinely need the underlying value (SDK proxies, serialization).
 */
sealed interface ConfigValue {
    val raw: Any

    data class BooleanValue(
        override val raw: Boolean,
    ) : ConfigValue

    data class StringValue(
        override val raw: String,
    ) : ConfigValue

    /**
     * A credential. [raw] is a [Secret], so this variant's own generated `toString` renders the mask
     * too — there is no arrangement of these types that prints the plaintext without a `reveal()`.
     */
    data class SecretValue(
        override val raw: Secret,
    ) : ConfigValue

    data class IntValue(
        override val raw: Int,
    ) : ConfigValue

    data class LongValue(
        override val raw: Long,
    ) : ConfigValue

    data class ShortValue(
        override val raw: Short,
    ) : ConfigValue

    data class FloatValue(
        override val raw: Float,
    ) : ConfigValue

    data class DoubleValue(
        override val raw: Double,
    ) : ConfigValue

    data class InstantValue(
        override val raw: Instant,
    ) : ConfigValue

    data class FileValue(
        override val raw: File,
    ) : ConfigValue

    data class EnumValue(
        override val raw: Enum<*>,
    ) : ConfigValue

    /**
     * A list value: the rows a user entered for a configuration item returning
     * `List<T : ScriptConfigurationListItem>`. See [ListRows] for why the rows are wrapped
     * rather than exposed as a bare `List`.
     */
    data class ListValue(
        override val raw: ListRows,
    ) : ConfigValue

    data class ProxyValue(
        override val raw: Proxy,
    ) : ConfigValue

    data class ProxyGroupValue(
        override val raw: ProxyGroup,
    ) : ConfigValue

    data class CustomDatasetGroupValue(
        override val raw: CustomDatasetGroup,
    ) : ConfigValue

    data class CustomDatasetItemValue(
        override val raw: CustomDatasetItem,
    ) : ConfigValue
}

/**
 * Whether this value is a group of records (a proxy group or custom dataset group). A script instance
 * that holds at least one group value can spawn multiple concurrent tasks.
 *
 * This is the single value-level source of truth for "is this a group?" — prefer it over `raw is Group<*>`
 * reflection. The type-level counterpart, used when only the definition (not a concrete value) is known,
 * is [com.cereal.client.domain.model.script.configuration.isGroup] on `ConfigItemType`.
 */
val ConfigValue.isGroup: Boolean
    get() = this is ConfigValue.ProxyGroupValue || this is ConfigValue.CustomDatasetGroupValue

/**
 * The wrapped [Group] when this value is a group ([isGroup]), otherwise `null`. Lets callers read
 * [Group.numberOfItems] polymorphically without unchecked `raw as? Group<*>` casts.
 */
val ConfigValue.asGroup: Group<*>?
    get() =
        when (this) {
            is ConfigValue.ProxyGroupValue -> raw
            is ConfigValue.CustomDatasetGroupValue -> raw
            else -> null
        }

/**
 * Wraps a raw runtime value into its corresponding [ConfigValue]. Used at the boundaries where untyped data
 * enters the configuration model (form input, dataset fields, database deserialization).
 *
 * One branch per supported value type: the count is the point of the function, and splitting the
 * dispatch table would obscure the mapping rather than simplify it.
 *
 * @throws UnsupportedConfigurationTypeException when the value type isn't a supported configuration value.
 *
 * The exhaustive `when` over every supported value type inherently raises cyclomatic complexity; splitting
 * it would obscure the mapping.
 */
@Suppress("CyclomaticComplexMethod")
fun Any.toConfigValue(): ConfigValue =
    when (this) {
        is Boolean -> ConfigValue.BooleanValue(this)

        is Secret -> ConfigValue.SecretValue(this)

        is String -> ConfigValue.StringValue(this)

        is Int -> ConfigValue.IntValue(this)

        is Long -> ConfigValue.LongValue(this)

        is Short -> ConfigValue.ShortValue(this)

        is Float -> ConfigValue.FloatValue(this)

        is Double -> ConfigValue.DoubleValue(this)

        is Instant -> ConfigValue.InstantValue(this)

        is File -> ConfigValue.FileValue(this)

        is Enum<*> -> ConfigValue.EnumValue(this)

        is ListRows -> ConfigValue.ListValue(this)

        is ProxyGroup -> ConfigValue.ProxyGroupValue(this)

        is Proxy -> ConfigValue.ProxyValue(this)

        is CustomDatasetGroup -> ConfigValue.CustomDatasetGroupValue(this)

        is CustomDatasetItem -> ConfigValue.CustomDatasetItemValue(this)

        // No branch for a bare List: the rows of a list configuration item arrive as [ListRows], and a
        // raw list is an unsupported value rather than something to coerce.
        else -> throw UnsupportedConfigurationTypeException("Unsupported value type: '${this.javaClass}'.")
    }
