package com.cereal.client.application.task

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptConfigurationDefinitionBuilder
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.ScriptConfigurationListItem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/**
 * Seam: the configuration invocation handler — the highest point of the script-facing bridge. Given a
 * definition and stored values, assert what the script's configuration method actually returns.
 */
class ConfigInvocationHandlerListTest {
    enum class Size { SMALL, LARGE }

    interface Target : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "sku", name = "SKU", description = "Product identifier", position = 0)
        fun sku(): String

        @ScriptConfigurationItem(keyName = "qty", name = "Quantity", description = "How many to buy", position = 1)
        fun qty(): Int

        @ScriptConfigurationItem(keyName = "ratio", name = "Ratio", description = "Split ratio", position = 2)
        fun ratio(): Float

        @ScriptConfigurationItem(keyName = "maxPrice", name = "Max price", description = "Price cap", position = 3)
        fun maxPrice(): Double

        @ScriptConfigurationItem(keyName = "size", name = "Size", description = "Which size", position = 4)
        fun size(): Size

        @ScriptConfigurationItem(keyName = "notify", name = "Notify", description = "Notify on success", position = 5)
        fun notify(): Boolean?

        @ScriptConfigurationItem(keyName = "note", name = "Note", description = "Free text", position = 6)
        fun note(): String?
    }

    interface TargetsConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 0)
        fun targets(): List<Target>
    }

    interface NullableTargetsConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 0)
        fun targets(): List<Target>?
    }

    private lateinit var scriptPackageInstance: ScriptPackageInstance

    @BeforeEach
    fun setUp() {
        // The handler resolves a dependency-injection scope on construction, so a container is required.
        startKoin { modules(module { scope<ScriptPackageInstance> { } }) }
        scriptPackageInstance = mockk { every { id } returns "instance-1" }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    private fun definitionOf(configuration: KClass<*>): ScriptConfigurationDefinition {
        @Suppress("UNCHECKED_CAST")
        return ScriptConfigurationDefinitionBuilder().createFrom(configuration as KClass<ScriptConfiguration>)
    }

    private inline fun <reified T : ScriptConfiguration> configurationProxy(
        definition: ScriptConfigurationDefinition,
        values: Map<String, ConfigValue>,
    ): T {
        val handler = ConfigInvocationHandler(scriptPackageInstance, definition, values)
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java), handler) as T
    }

    private fun completeRow(
        sku: String,
        qty: Int,
    ) = ListRow(
        mapOf(
            "sku" to ConfigValue.StringValue(sku),
            "qty" to ConfigValue.IntValue(qty),
            "ratio" to ConfigValue.FloatValue(0.5f),
            "maxPrice" to ConfigValue.DoubleValue(19.99),
            "size" to ConfigValue.EnumValue(Size.LARGE),
        ),
    )

    @Test
    fun `returns one typed object per stored row`() {
        val definition = definitionOf(TargetsConfig::class)
        val configuration =
            configurationProxy<TargetsConfig>(
                definition,
                mapOf(
                    "targets" to
                        ConfigValue.ListValue(
                            ListRows(listOf(completeRow("ABC-123", 2), completeRow("XYZ-9", 1))),
                        ),
                ),
            )

        val targets = configuration.targets()

        assertEquals(2, targets.size)
        assertEquals(listOf("ABC-123", "XYZ-9"), targets.map { it.sku() })
        assertEquals(listOf(2, 1), targets.map { it.qty() })
    }

    @Test
    fun `returns each field as its declared type`() {
        val definition = definitionOf(TargetsConfig::class)
        val configuration =
            configurationProxy<TargetsConfig>(
                definition,
                mapOf("targets" to ConfigValue.ListValue(ListRows(listOf(completeRow("ABC-123", 2))))),
            )

        val target = configuration.targets().single()

        assertEquals("ABC-123", target.sku())
        assertEquals(2, target.qty())
        assertEquals(0.5f, target.ratio())
        assertEquals(19.99, target.maxPrice())
        assertEquals(Size.LARGE, target.size())
    }

    @Test
    fun `returns null for a nullable field the user left blank`() {
        val definition = definitionOf(TargetsConfig::class)
        val configuration =
            configurationProxy<TargetsConfig>(
                definition,
                mapOf("targets" to ConfigValue.ListValue(ListRows(listOf(completeRow("ABC-123", 2))))),
            )

        val target = configuration.targets().single()

        assertNull(target.notify())
        assertNull(target.note())
    }

    @Test
    fun `returns a nullable field value when the user supplied one`() {
        val row =
            ListRow(
                completeRow("ABC-123", 2).fields +
                    mapOf(
                        "notify" to ConfigValue.BooleanValue(true),
                        "note" to ConfigValue.StringValue("hurry"),
                    ),
            )
        val configuration =
            configurationProxy<TargetsConfig>(
                definitionOf(TargetsConfig::class),
                mapOf("targets" to ConfigValue.ListValue(ListRows(listOf(row)))),
            )

        val target = configuration.targets().single()

        assertEquals(true, target.notify())
        assertEquals("hurry", target.note())
    }

    @Test
    fun `throws when a non-nullable field has no stored value`() {
        val incompleteRow = ListRow(completeRow("ABC-123", 2).fields - "qty")
        val configuration =
            configurationProxy<TargetsConfig>(
                definitionOf(TargetsConfig::class),
                mapOf("targets" to ConfigValue.ListValue(ListRows(listOf(incompleteRow)))),
            )

        val exception = assertThrows<RuntimeException> { configuration.targets().single().qty() }

        assertEquals(true, exception.message!!.contains("qty"), exception.message)
    }

    @Test
    fun `throws when a non-nullable list has no stored value`() {
        val configuration = configurationProxy<TargetsConfig>(definitionOf(TargetsConfig::class), emptyMap())

        assertThrows<RuntimeException> { configuration.targets() }
    }

    @Test
    fun `returns null when a nullable list has no stored value`() {
        val configuration =
            configurationProxy<NullableTargetsConfig>(definitionOf(NullableTargetsConfig::class), emptyMap())

        assertNull(configuration.targets())
    }

    @Test
    fun `returns null when a nullable list stores zero rows`() {
        val configuration =
            configurationProxy<NullableTargetsConfig>(
                definitionOf(NullableTargetsConfig::class),
                mapOf("targets" to ConfigValue.ListValue(ListRows.EMPTY)),
            )

        assertNull(configuration.targets())
    }
}
