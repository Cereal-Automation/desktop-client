package com.cereal.client.application.task

import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.script.configuration.itemForKey
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.models.proxy.RandomProxy
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinFunction
import com.cereal.sdk.models.Secret as SdkSecret
import com.cereal.sdk.models.proxy.Proxy as SdkProxy
import java.lang.reflect.Proxy as JavaProxy

/**
 * An invocation handler that dynamically provides values for methods annotated with
 * `@ScriptConfigurationItem` or `@TaskConfigurationItem` from a provided configuration.
 *
 * This class intercepts method calls on a proxy object and returns appropriate configuration
 * values based on the method annotations and the provided configuration map.
 *
 * @property configurationDefinition The definition of the script configuration containing default values.
 * @property configuration A map of task configuration values keyed by configuration item names.
 *
 * TODO: Move this class to the data layer (because of the interaction with the ScriptConfigurationItem annotations).
 */
class ConfigInvocationHandler(
    scriptPackageInstance: ScriptPackageInstance,
    private val configurationDefinition: ScriptConfigurationDefinition,
    private val configuration: ScriptConfigurationValues,
) : InvocationHandler,
    KoinComponent {
    private val logger = LoggerFactory.getLogger(ConfigInvocationHandler::class.java)
    private val scriptPackageInstanceScope =
        getKoin().getOrCreateScope(
            scriptPackageInstance.id,
            named<ScriptPackageInstance>(),
            source = scriptPackageInstance,
        )

    /**
     * Caches reflection metadata (annotation + Kotlin return type) per [Method] to avoid
     * repeated reflection lookups on every invocation during the script execution loop.
     * The configuration interface is fixed at runtime, so these values never change.
     */
    private val methodMetadataCache = ConcurrentHashMap<Method, MethodMetadata>()

    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any>?,
    ): Any? {
        val iface = proxy.javaClass.interfaces[0]

        return when {
            method.name == "toString" && args == null -> iface.simpleName
            method.name == "hashCode" && args == null -> System.identityHashCode(proxy)
            method.name == "equals" && args != null && args.size == 1 -> proxy === args[0]
            else -> handleConfigurationMethod(method)
        }
    }

    private fun getMethodMetadata(method: Method): MethodMetadata =
        methodMetadataCache.getOrPut(method) {
            val annotation = method.getAnnotation(ScriptConfigurationItem::class.java)
            val returnType =
                method.kotlinFunction?.returnType
                    ?: throw RuntimeException("Method ${method.name} requires a return type to be defined.")
            MethodMetadata(annotation, returnType)
        }

    private fun handleConfigurationMethod(method: Method): Any? {
        val metadata = getMethodMetadata(method)

        return getConfigValue(metadata.annotation, metadata.returnType)?.let { value ->
            if (method.returnType.kotlin.isInstance(value)) {
                return value
            } else if (metadata.returnType.isMarkedNullable) {
                // Fallback to null if return type supports it.
                logger.warn(
                    "For method '$method' fallback to null because the methods return type '${method.returnType}' doesn't match the type of the actual value we have '${value.javaClass}'.",
                )
                return null
            } else {
                throw RuntimeException(
                    "Configuration methods return type '${method.returnType}' doesn't match the type of the actual value '${value.javaClass}', therefor we can't return a value.",
                )
            }
        } ?: run {
            if (metadata.returnType.isMarkedNullable) {
                return null
            } else {
                throw RuntimeException(
                    "No value found for configuration method '$method' and because this method doesn't have a nullable return type we can't resolve this methods value.",
                )
            }
        }
    }

    private fun getConfigValue(
        annotation: ScriptConfigurationItem,
        methodReturnType: KType,
    ): Any? {
        val value = configuration[annotation.keyName]

        return when (methodReturnType.classifier) {
            SdkProxy::class -> {
                (value as? ConfigValue.ProxyValue)?.raw?.toComponentProxy()
            }

            RandomProxy::class -> {
                (value as? ConfigValue.ProxyGroupValue)?.raw?.toComponentRandomProxy()
            }

            // Handled before the generic branch because the script's method returns the *SDK* Secret
            // while the configuration holds the *domain* Secret. Covers per-task credentials too, so
            // each task's own value is converted rather than failing the return-type check.
            SdkSecret::class -> {
                (rawValueFor(annotation) as? Secret)?.toComponentSecret()
            }

            // A list resolves to one dynamic proxy per row. It has to be handled here alongside
            // the proxy cases rather than on the generic path: the stored value is a list of per-row field
            // maps, which is not an instance of the method's declared List<T> return type.
            //
            // The record type is always present: every list configuration item is a list of records, and
            // one whose element type is not a record is rejected when the script is loaded.
            List::class -> {
                val itemType = listItemType(annotation.keyName)
                // No rows means "not configured": a nullable list then arrives as null and a
                // non-nullable one raises the same failure as any other missing required value.
                (value as? ConfigValue.ListValue)
                    ?.raw
                    ?.rows
                    ?.takeIf { it.isNotEmpty() }
                    ?.map { row -> createListRowProxy(itemType, annotation.keyName, row) }
            }

            else -> {
                rawValueFor(annotation)
            }
        }
    }

    /**
     * The raw configured value for [annotation], read from the task's own dataset record when the item
     * is declared per-task, and falling back to the definition's default value.
     */
    private fun rawValueFor(annotation: ScriptConfigurationItem): Any? =
        if (annotation.valuePerTask) {
            // Get value from the custom dataset item.
            (configuration[ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key] as? ConfigValue.CustomDatasetItemValue)
                ?.raw
                ?.fields
                ?.get(annotation.keyName)
                ?.raw
                ?: getDefaultValue(annotation.keyName)
        } else {
            configuration[annotation.keyName]?.raw ?: getDefaultValue(annotation.keyName)
        }

    private fun getDefaultValue(key: String): Any? = configurationDefinition.itemForKey(key)?.defaultValue?.raw

    /**
     * The record interface behind the list declared for [key]. A configuration item returning a `List`
     * is always a list of records, so a definition saying otherwise is a broken definition rather than
     * a shape to fall back on — hence the loud failure instead of a null.
     */
    private fun listItemType(key: String): KClass<*> =
        (configurationDefinition.itemForKey(key)?.type as? ConfigItemType.ListConfigItem)?.itemType
            ?: throw RuntimeException(
                "Configuration item '$key' returns a List but is not declared as a list of " +
                    "ScriptConfigurationListItem records, so its rows cannot be resolved.",
            )

    /**
     * Hands the script one dynamic proxy per row of a list. The proxy resolves each method to a
     * field key via its `@ScriptConfigurationItem` annotation and returns the stored typed value, so the
     * script reads `row.qty()` as an `Int` instead of parsing a substring.
     */
    private fun createListRowProxy(
        itemType: KClass<*>,
        listItemKey: String,
        row: ListRow,
    ): Any =
        JavaProxy.newProxyInstance(
            itemType.java.classLoader,
            arrayOf(itemType.java),
            ListRowInvocationHandler(listItemKey, row),
        )

    /**
     * Resolves the fields of a single list row. Nullability is honoured the same way top-level
     * items are: a blank nullable field is `null`, a missing non-nullable field is a failure rather than a
     * silent default.
     */
    private class ListRowInvocationHandler(
        private val listItemKey: String,
        private val row: ListRow,
    ) : InvocationHandler {
        override fun invoke(
            proxy: Any,
            method: Method,
            args: Array<out Any>?,
        ): Any? =
            when {
                method.name == "toString" && args == null -> "$listItemKey row [${row.describe()}]"
                method.name == "hashCode" && args == null -> System.identityHashCode(proxy)
                method.name == "equals" && args != null && args.size == 1 -> proxy === args[0]
                else -> resolveField(method)
            }

        private fun resolveField(method: Method): Any? {
            val annotation =
                method.getAnnotation(ScriptConfigurationItem::class.java)
                    ?: throw RuntimeException(
                        "Method '${method.name}' of the record used by list '$listItemKey' is not annotated " +
                            "with @ScriptConfigurationItem, so it has no configured value.",
                    )

            row.fields[annotation.keyName]?.raw?.let { return it }

            if (method.kotlinFunction?.returnType?.isMarkedNullable == true) {
                return null
            }
            throw RuntimeException(
                "No value found for field '${annotation.keyName}' of list '$listItemKey' and because this " +
                    "field doesn't have a nullable return type we can't resolve its value.",
            )
        }
    }

    private fun ProxyGroup.toComponentRandomProxy(): RandomProxy {
        val proxiesRandomizerProvider =
            scriptPackageInstanceScope.get<ProxiesRandomizerProvider>(ProxiesRandomizerProvider::class)
        return RandomProxyImpl(this, proxiesRandomizerProvider)
    }

    /**
     * Holds cached reflection metadata for a configuration method to avoid repeated
     * annotation lookups and Kotlin function resolution on every proxy invocation.
     */
    private data class MethodMetadata(
        val annotation: ScriptConfigurationItem,
        val returnType: KType,
    )
}
