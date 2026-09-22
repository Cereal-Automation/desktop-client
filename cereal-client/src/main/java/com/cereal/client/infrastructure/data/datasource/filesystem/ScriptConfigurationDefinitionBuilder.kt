package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.exception.InvalidScriptConfigurationDefinitionException
import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.toConfigValue
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.ScriptConfigurationListItem
import com.cereal.sdk.models.proxy.Proxy
import com.cereal.sdk.models.proxy.RandomProxy
import com.cereal.sdk.statemodifier.DefaultStateModifier
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import com.cereal.sdk.models.Secret as SdkSecret
import java.lang.reflect.Proxy as JavaProxy

class ScriptConfigurationDefinitionBuilder {
    companion object {
        private val logger = LoggerFactory.getLogger(ScriptConfigurationDefinitionBuilder::class.java)

        private val ALLOWED_CONFIGURATION_METHOD_RETURN_TYPES =
            setOf(
                Boolean::class,
                String::class,
                SdkSecret::class,
                Int::class,
                Float::class,
                Double::class,
                Enum::class,
                Proxy::class,
                RandomProxy::class,
                List::class,
            )
        private val ALLOWED_VALUE_PER_TASK_CONFIGURATION_METHOD_RETURN_TYPES =
            setOf(
                String::class,
                SdkSecret::class,
                Int::class,
                Float::class,
                Double::class,
            )

        /**
         * The field types permitted inside a list's record. Every one maps to a form widget that
         * already exists, and none of them makes the definition tree recursive.
         */
        private val ALLOWED_LIST_FIELD_RETURN_TYPES =
            setOf(
                Boolean::class,
                String::class,
                Int::class,
                Float::class,
                Double::class,
                Enum::class,
            )
    }

    fun createFrom(scriptConfigurationClass: KClass<ScriptConfiguration>): ScriptConfigurationDefinition {
        val configurationItems = getConfigurationItems(scriptConfigurationClass)
        validateConfigurationItems(configurationItems)

        return ScriptConfigurationDefinition(
            scriptConfigurationClass = scriptConfigurationClass,
            configurationItems = configurationItems,
        )
    }

    private fun validateConfigurationItems(configurationItemDefinitions: List<ScriptConfigurationItemDefinition>) {
        // Only a single Proxy config may exist because the TaskConfigurationBuilder expects just one.
        if (configurationItemDefinitions.count { it.type is ConfigItemType.ProxyConfigItem } > 1) {
            throw InvalidScriptConfigurationDefinitionException(
                "The script contains an invalid configuration: multiple Proxy config items found.",
            )
        }

        // A secret cannot identify a script: masked, every instance would display identically and be
        // indistinguishable in the script list; unmasked, it would punch a hole through the display
        // guarantee the type exists to provide. Rejected at load time so the developer finds out
        // rather than shipping a script that misbehaves in front of users.
        // Checked against the flattened list, not just the top level: a per-task item is nested inside
        // the synthetic "Task data" grouped item, so a top-level-only scan would let a per-task secret
        // through — and per-task secrets are legal, so that combination is reachable.
        configurationItemDefinitions
            .flattenGroupedItems()
            .firstOrNull { it.isScriptIdentifier && it.type is ConfigItemType.SecretConfigItem }
            ?.let { item ->
                throw InvalidScriptConfigurationDefinitionException(
                    "The script contains an invalid configuration: the '${item.name}' config item is a Secret and " +
                        "cannot be the script identifier, because the identifier is displayed and a secret is masked.",
                )
            }
    }

    /** These definitions plus the per-task field definitions nested inside any grouped item. */
    private fun List<ScriptConfigurationItemDefinition>.flattenGroupedItems(): List<ScriptConfigurationItemDefinition> =
        flatMap { definition ->
            listOf(definition) + ((definition.type as? ConfigItemType.GroupedConfigItem)?.items.orEmpty())
        }

    private fun getConfigurationItems(scriptConfigurationClass: KClass<ScriptConfiguration>): List<ScriptConfigurationItemDefinition> {
        val scriptConfigurationMethods = getScriptConfigurationMethods(scriptConfigurationClass)
        val definitions = mutableListOf<ScriptConfigurationItemDefinition>()
        val datasetDefinitions = mutableListOf<ScriptConfigurationItemDefinition>()

        // Create a proxy to extract default values from interface methods
        val defaultValuesProxy = createDefaultValuesProxy(scriptConfigurationClass)

        scriptConfigurationMethods.forEach { item ->
            // Please note that the state modifier must be an object and not a class because we only read the objectInstance.
            val stateModifier =
                item.annotation.stateModifier.objectInstance
                    ?: throw RuntimeException(
                        "The '${item.annotation.name}' configuration method contains a" +
                            " stateModifier which isn't an object. Most likely you defined the stateModifier" +
                            " as a class, change it to an object instead. Another reason could be that you're missing" +
                            " proguard rules to keep all Kotlin object singletons in your package.",
                    )

            // Extract default value from the interface method if it has a default implementation
            val defaultValue = extractDefaultValue(defaultValuesProxy, item)

            val definition =
                ScriptConfigurationItemDefinition(
                    name = item.annotation.name,
                    description = item.annotation.description,
                    key = item.annotation.keyName,
                    position = Int.MAX_VALUE,
                    type = item.toConfigItemType(),
                    isNullable = item.isNullable,
                    stateModifier = stateModifier,
                    isScriptIdentifier = item.annotation.isScriptIdentifier,
                    defaultValue = defaultValue,
                )

            if (item.annotation.valuePerTask) {
                datasetDefinitions.add(definition)
            } else {
                definitions.add(definition)
            }
        }

        if (datasetDefinitions.isNotEmpty()) {
            val datasetDefinition =
                ScriptConfigurationItemDefinition(
                    name = "Task data",
                    description = "Data used for input to each task.",
                    key = ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key,
                    position = Int.MAX_VALUE,
                    type = ConfigItemType.GroupedConfigItem(datasetDefinitions),
                    isNullable = datasetDefinitions.all { it.isNullable },
                    stateModifier = TaskDataStateModifier(datasetDefinitions),
                    isScriptIdentifier = false,
                )
            definitions.add(datasetDefinition)
        }

        return definitions
    }

    private fun getScriptConfigurationMethods(scriptConfigurationClass: KClass<ScriptConfiguration>): List<ScriptConfigurationMethod> =
        scriptConfigurationClass.memberFunctions
            .filter { method ->
                method.typeParameters.isEmpty() &&
                    method
                        .findAnnotations(ScriptConfigurationItem::class)
                        .isNotEmpty() &&
                    method.name !in setOf("toString", "equals", "hashCode")
            }.map { method ->
                val annotation = method.findAnnotations(ScriptConfigurationItem::class).first()
                val listItemType = method.listItemType()

                if (listItemType != null) {
                    validateListItem(method, annotation)
                } else {
                    validateReturnType(method, annotation)
                }

                ScriptConfigurationMethod(
                    annotation,
                    method.returnType.classifier as KClass<*>,
                    method.returnType.isMarkedNullable,
                    method,
                    listItemType,
                )
            }.sortedWith(
                compareBy({ it.annotation.position }, { it.annotation.name }),
            )

    private fun validateReturnType(
        method: kotlin.reflect.KFunction<*>,
        annotation: ScriptConfigurationItem,
    ) {
        val allowedReturnTypes =
            if (annotation.valuePerTask) ALLOWED_VALUE_PER_TASK_CONFIGURATION_METHOD_RETURN_TYPES else ALLOWED_CONFIGURATION_METHOD_RETURN_TYPES

        // The isAssignableFrom is needed for the Enum check.
        val kClassReturnType = method.returnType.classifier as? KClass<*>
        if (kClassReturnType == null ||
            (!allowedReturnTypes.contains(kClassReturnType) && !kClassReturnType.java.isEnum)
        ) {
            throw UnsupportedConfigurationTypeException(
                "ScriptConfigurationItem '${method.name}' has an unsupported type: ${method.returnType}.",
            )
        }

        // A list is always a list of records. The caller has already routed a `List<T>` whose element
        // type implements the SDK marker interface down the list branch, so any List still arriving
        // here declares an element type that is not a record.
        if (kClassReturnType == List::class) {
            val typeArg =
                method.returnType.arguments
                    .firstOrNull()
                    ?.type
                    ?.classifier
            throw UnsupportedConfigurationTypeException(
                "ScriptConfigurationItem '${method.name}' has an unsupported List type argument: $typeArg. " +
                    "A list configuration item must be a List<T> whose element type is an interface extending " +
                    "ScriptConfigurationListItem. For a list of single values, declare a record with one field.",
            )
        }
    }

    /**
     * The record interface behind a list item, or null when this method does not declare one.
     * A list item is a `List<T>` whose element type implements the SDK's marker interface.
     */
    private fun kotlin.reflect.KFunction<*>.listItemType(): KClass<*>? {
        if (returnType.classifier != List::class) return null
        val typeArg =
            returnType.arguments
                .firstOrNull()
                ?.type
                ?.classifier as? KClass<*>
        return typeArg?.takeIf { it.isSubclassOf(ScriptConfigurationListItem::class) }
    }

    /**
     * Enforces the rules a list item must satisfy. Each failure names the fix, because these are
     * authoring mistakes that would otherwise surface as a broken configuration form or a script that
     * silently receives nothing.
     */
    private fun validateListItem(
        method: kotlin.reflect.KFunction<*>,
        annotation: ScriptConfigurationItem,
    ) {
        if (annotation.valuePerTask) {
            throw InvalidScriptConfigurationDefinitionException(
                "ScriptConfigurationItem '${method.name}' combines a list with valuePerTask, which is not " +
                    "supported: a list is one value for one script run and never fans out into tasks. " +
                    "Use Task data (valuePerTask on singular items) to start one task per row instead.",
            )
        }

        if (annotation.isScriptIdentifier) {
            throw InvalidScriptConfigurationDefinitionException(
                "ScriptConfigurationItem '${method.name}' sets isScriptIdentifier on a list, which cannot " +
                    "identify a script instance. Mark a singular String, Int, Float, Double or enum item instead.",
            )
        }

        if (method.hasDefaultImplementation()) {
            throw InvalidScriptConfigurationDefinitionException(
                "ScriptConfigurationItem '${method.name}' has a default implementation, but lists do not " +
                    "support default values or pre-seeded rows. Remove the default implementation; supply rows in " +
                    "your unit tests by overriding the method in your test configuration object.",
            )
        }
    }

    /**
     * Whether an interface method carries a body. Both shapes are checked because a script JAR may be
     * compiled with JVM default methods enabled or with Kotlin's `DefaultImpls` class.
     */
    private fun kotlin.reflect.KFunction<*>.hasDefaultImplementation(): Boolean {
        val javaMethod = javaMethod ?: return false
        if (javaMethod.isDefault) return true
        return try {
            Class
                .forName(
                    "${javaMethod.declaringClass.name}\$DefaultImpls",
                    false,
                    javaMethod.declaringClass.classLoader,
                ).methods
                .any { it.name == javaMethod.name && it.parameterCount == 1 }
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * Reflects the fields of a list's record interface — one level deep, so the definition tree
     * stays non-recursive.
     */
    private fun reflectListFields(
        itemType: KClass<*>,
        listItemName: String,
    ): List<ScriptConfigurationItemDefinition> {
        val definitions =
            itemType.memberFunctions
                .filter { it.typeParameters.isEmpty() && it.findAnnotations(ScriptConfigurationItem::class).isNotEmpty() }
                .map { field ->
                    val annotation = field.findAnnotations(ScriptConfigurationItem::class).first()

                    if (annotation.stateModifier != DefaultStateModifier::class) {
                        throw InvalidScriptConfigurationDefinitionException(
                            "Field '${annotation.keyName}' of the record used by list '$listItemName' declares a " +
                                "stateModifier. Visibility and validation belong to the list item itself — move the " +
                                "stateModifier to '$listItemName'.",
                        )
                    }

                    if (field.hasDefaultImplementation()) {
                        throw InvalidScriptConfigurationDefinitionException(
                            "Field '${annotation.keyName}' of the record used by list '$listItemName' has a " +
                                "default implementation, but lists do not support default values or pre-seeded " +
                                "rows. Remove the default implementation.",
                        )
                    }

                    ScriptConfigurationItemDefinition(
                        name = annotation.name,
                        description = annotation.description,
                        key = annotation.keyName,
                        position = annotation.position,
                        type = listFieldType(field, listItemName),
                        isNullable = field.returnType.isMarkedNullable,
                        stateModifier = DefaultStateModifier,
                        isScriptIdentifier = false,
                    )
                }.sortedWith(compareBy({ it.position }, { it.name }))

        definitions
            .groupBy { it.key }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { duplicateKey ->
                throw InvalidScriptConfigurationDefinitionException(
                    "The record used by list '$listItemName' declares more than one field with keyName " +
                        "'$duplicateKey'. Give every field in a record its own keyName.",
                )
            }

        return definitions
    }

    private fun listFieldType(
        field: kotlin.reflect.KFunction<*>,
        listItemName: String,
    ): ConfigItemType {
        val kClassReturnType = field.returnType.classifier as? KClass<*>
        if (kClassReturnType == null ||
            (!ALLOWED_LIST_FIELD_RETURN_TYPES.contains(kClassReturnType) && !kClassReturnType.java.isEnum)
        ) {
            throw InvalidScriptConfigurationDefinitionException(
                "Field '${field.name}' of the record used by list '$listItemName' has unsupported type " +
                    "'${field.returnType}'. A record field must be a String, Int, Float, Double, Boolean or enum " +
                    "(optionally nullable).",
            )
        }
        return kClassReturnType.toConfigItemType()
    }

    private fun ScriptConfigurationMethod.toConfigItemType(): ConfigItemType =
        listItemType?.let { itemType ->
            ConfigItemType.ListConfigItem(
                itemType = itemType,
                items = reflectListFields(itemType, annotation.keyName),
            )
        } ?: returnType.toConfigItemType()

    private fun KClass<*>.toConfigItemType(): ConfigItemType =
        if (this.java.isEnum) {
            @Suppress("UNCHECKED_CAST")
            ConfigItemType.EnumConfigItem(this as KClass<Enum<*>>)
        } else {
            when (this) {
                Boolean::class -> {
                    ConfigItemType.BooleanConfigItem
                }

                String::class -> {
                    ConfigItemType.StringConfigItem
                }

                SdkSecret::class -> {
                    ConfigItemType.SecretConfigItem
                }

                Int::class -> {
                    ConfigItemType.IntConfigItem
                }

                Float::class -> {
                    ConfigItemType.FloatConfigItem
                }

                Double::class -> {
                    ConfigItemType.DoubleConfigItem
                }

                Proxy::class -> {
                    ConfigItemType.ProxyConfigItem
                }

                RandomProxy::class -> {
                    ConfigItemType.ProxyGroupConfigItem
                }

                // No branch for List: a list is always a list of records and is mapped by the caller
                // from its element type, so a bare List never reaches here — validateReturnType has
                // already rejected it.
                else -> {
                    throw RuntimeException("No mapping for class: $this")
                }
            }
        }

    /**
     * Creates a proxy instance to invoke default interface methods.
     * This allows us to extract default values from Kotlin interface methods with default implementations.
     */
    private fun createDefaultValuesProxy(scriptConfigurationClass: KClass<ScriptConfiguration>): ScriptConfiguration =
        JavaProxy.newProxyInstance(
            scriptConfigurationClass.java.classLoader,
            arrayOf(scriptConfigurationClass.java),
            DefaultValueInvocationHandler(),
        ) as ScriptConfiguration

    /**
     * Extracts the default value from an interface method if it has a default implementation.
     * Returns null if the method doesn't have a default implementation or if invocation fails.
     */
    private fun extractDefaultValue(
        proxy: ScriptConfiguration,
        item: ScriptConfigurationMethod,
    ): ConfigValue? {
        val javaMethod = item.method.javaMethod ?: return null

        // Only extract defaults for primitive types, String, and Enum - not for complex types like Proxy, Account, etc.
        // Secret is deliberately absent: a default returning a credential would be a hardcoded secret
        // in source. `ScriptConfigurationDefinitionBuilderTest` asserts this rather than leaving it to
        // fall out of the filter, so removing Secret from the exclusion set fails a test.
        val supportedDefaultTypes =
            setOf(
                Boolean::class,
                String::class,
                Int::class,
                Float::class,
                Double::class,
            )
        val isEnumType = item.returnType.java.isEnum
        if (!supportedDefaultTypes.contains(item.returnType) && !isEnumType) {
            return null
        }

        return try {
            // Check if the method has a default implementation
            val rawDefault =
                if (javaMethod.isDefault) {
                    // For Java default methods, use privateLookupIn for proper access
                    invokeDefaultMethod(proxy, javaMethod)
                } else {
                    // For Kotlin default interface methods, try invoking the DefaultImpls class
                    invokeKotlinDefaultMethod(proxy, javaMethod)
                }
            rawDefault?.toConfigValue()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // If extraction fails, return null - no default value available. Report so the extraction
            // defect is visible rather than silently degrading to "no default".
            logger.warn("Failed to extract default value for config item '${item.method.name}'", e)
            CrashReporter.report(e)
            null
        }
    }

    /**
     * Invokes a Java default interface method.
     * Uses InvocationHandler.invokeDefault (Java 16+) which is the official way to invoke
     * default methods on proxy instances without requiring special access privileges.
     */
    private fun invokeDefaultMethod(
        proxy: ScriptConfiguration,
        javaMethod: Method,
    ): Any? {
        // InvocationHandler.invokeDefault is the proper way to invoke default methods on proxies (Java 16+)
        // It doesn't require MethodHandles.privateLookupIn access which fails for classes from different classloaders
        return InvocationHandler.invokeDefault(proxy, javaMethod, *arrayOf<Any>())
    }

    /**
     * Invokes a Kotlin default interface method via the generated DefaultImpls class.
     */
    private fun invokeKotlinDefaultMethod(
        proxy: ScriptConfiguration,
        javaMethod: Method,
    ): Any? {
        // Pin the lookup to the declaring class's classloader rather than letting
        // Class.forName resolve via the caller's loader and walk up.
        val declaringClassLoader = javaMethod.declaringClass.classLoader
        val defaultImplsClass =
            Class.forName(
                "${javaMethod.declaringClass.name}\$DefaultImpls",
                false,
                declaringClassLoader,
            )
        val defaultMethod =
            defaultImplsClass.methods.find {
                it.name == javaMethod.name && it.parameterCount == 1
            }
        return defaultMethod?.invoke(null, proxy)
    }

    /**
     * Invocation handler used for extracting default values.
     * Returns null for all method invocations since we use special handling for default methods.
     */
    private class DefaultValueInvocationHandler : InvocationHandler {
        override fun invoke(
            proxy: Any,
            method: Method,
            args: Array<out Any>?,
        ): Any? =
            when (method.name) {
                "toString" -> "DefaultValuesProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
    }

    data class ScriptConfigurationMethod(
        val annotation: ScriptConfigurationItem,
        val returnType: KClass<*>,
        val isNullable: Boolean,
        val method: kotlin.reflect.KFunction<*>,
        /** The record interface when this method declares a list, otherwise null. */
        val listItemType: KClass<*>? = null,
    )
}
