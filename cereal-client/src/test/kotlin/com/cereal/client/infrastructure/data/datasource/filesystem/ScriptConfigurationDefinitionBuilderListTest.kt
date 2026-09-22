package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.exception.InvalidScriptConfigurationDefinitionException
import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.ScriptConfigurationListItem
import com.cereal.sdk.models.proxy.Proxy
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

/**
 * Seam: the script configuration definition builder. Input is a configuration interface fixture,
 * output is a definition or a thrown failure — no test doubles.
 */
class ScriptConfigurationDefinitionBuilderListTest {
    private val builder = ScriptConfigurationDefinitionBuilder()

    enum class Size { SMALL, MEDIUM }

    interface Target : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "sku", name = "SKU", description = "Product identifier", position = 0)
        fun sku(): String

        @ScriptConfigurationItem(keyName = "qty", name = "Quantity", description = "How many to buy", position = 1)
        fun qty(): Int

        @ScriptConfigurationItem(keyName = "notify", name = "Notify", description = "Notify on success", position = 2)
        fun notify(): Boolean?

        @ScriptConfigurationItem(keyName = "size", name = "Size", description = "Which size", position = 3)
        fun size(): Size

        @ScriptConfigurationItem(keyName = "maxPrice", name = "Max price", description = "Price cap", position = 4)
        fun maxPrice(): Double?
    }

    interface Watch : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "url", name = "URL", description = "Page to watch", position = 0)
        fun url(): String
    }

    interface ListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<Target>
    }

    interface NullableListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<Target>?
    }

    interface TwoListsConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<Target>

        @ScriptConfigurationItem(keyName = "watches", name = "Watches", description = "Pages", position = 2)
        fun watches(): List<Watch>
    }

    interface PerTaskListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(
            keyName = "targets",
            name = "Targets",
            description = "Products",
            position = 1,
            valuePerTask = true,
        )
        fun targets(): List<Target>
    }

    interface IdentifierListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(
            keyName = "targets",
            name = "Targets",
            description = "Products",
            position = 1,
            isScriptIdentifier = true,
        )
        fun targets(): List<Target>
    }

    interface DefaultListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<Target> = emptyList()
    }

    object AlwaysVisible : StateModifier {
        override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleOptional

        override fun getError(scriptConfig: ScriptConfig): String? = null
    }

    interface FieldStateModifierItem : ScriptConfigurationListItem {
        @ScriptConfigurationItem(
            keyName = "sku",
            name = "SKU",
            description = "Product identifier",
            position = 0,
            stateModifier = AlwaysVisible::class,
        )
        fun sku(): String
    }

    interface FieldStateModifierConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<FieldStateModifierItem>
    }

    interface ProxyFieldItem : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "proxy", name = "Proxy", description = "Proxy to use", position = 0)
        fun proxy(): Proxy
    }

    interface ProxyFieldConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<ProxyFieldItem>
    }

    interface NestedListFieldItem : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "tags", name = "Tags", description = "Tags", position = 0)
        fun tags(): List<String>
    }

    interface NestedListFieldConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<NestedListFieldItem>
    }

    interface NestedRecordFieldItem : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "nested", name = "Nested", description = "Nested record", position = 0)
        fun nested(): Watch
    }

    interface NestedRecordFieldConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<NestedRecordFieldItem>
    }

    interface DuplicateKeyItem : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "sku", name = "SKU", description = "Product identifier", position = 0)
        fun sku(): String

        @ScriptConfigurationItem(keyName = "sku", name = "Code", description = "Product code", position = 1)
        fun code(): String
    }

    interface DuplicateKeyConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<DuplicateKeyItem>
    }

    interface ReusedTopLevelKeyItem : ScriptConfigurationListItem {
        @ScriptConfigurationItem(keyName = "name", name = "Name", description = "Row name", position = 0)
        fun name(): String
    }

    interface ReusedTopLevelKeyConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "name", name = "Name", description = "Script name", position = 0)
        fun name(): String

        @ScriptConfigurationItem(keyName = "targets", name = "Targets", description = "Products", position = 1)
        fun targets(): List<ReusedTopLevelKeyItem>
    }

    interface OneFieldConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "watches", name = "Watches", description = "Pages", position = 1)
        fun watches(): List<Watch>
    }

    interface StringListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "tags", name = "Tags", description = "Tag list", position = 1)
        fun tags(): List<String>
    }

    interface NullableStringListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "tags", name = "Tags", description = "Tag list", position = 1)
        fun tags(): List<String>?
    }

    interface IntListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "nums", name = "Numbers", description = "Numbers", position = 1)
        fun numbers(): List<Int>
    }

    interface EnumListConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "sizes", name = "Sizes", description = "Sizes", position = 1)
        fun sizes(): List<Size>
    }

    @Suppress("UNCHECKED_CAST")
    private fun createFrom(configuration: KClass<*>) = builder.createFrom(configuration as KClass<ScriptConfiguration>)

    @Test
    fun `createFrom produces an ListConfigItem carrying the record type`() {
        val item = createFrom(ListConfig::class).configurationItems.single()

        val type = item.type as ConfigItemType.ListConfigItem
        assertEquals("targets", item.key)
        assertEquals(Target::class, type.itemType)
        assertFalse(item.isNullable)
    }

    @Test
    fun `createFrom reflects each record field with its own name, description and type`() {
        val type =
            createFrom(ListConfig::class)
                .configurationItems
                .single()
                .type as ConfigItemType.ListConfigItem

        assertEquals(listOf("sku", "qty", "notify", "size", "maxPrice"), type.items.map { it.key })
        assertEquals(listOf("SKU", "Quantity", "Notify", "Size", "Max price"), type.items.map { it.name })
        assertEquals("Product identifier", type.items.first().description)
        assertEquals(
            listOf<ConfigItemType>(
                ConfigItemType.StringConfigItem,
                ConfigItemType.IntConfigItem,
                ConfigItemType.BooleanConfigItem,
                ConfigItemType.EnumConfigItem(@Suppress("UNCHECKED_CAST") (Size::class as KClass<Enum<*>>)),
                ConfigItemType.DoubleConfigItem,
            ),
            type.items.map { it.type },
        )
    }

    @Test
    fun `createFrom marks a nullable record field as nullable`() {
        val type =
            createFrom(ListConfig::class)
                .configurationItems
                .single()
                .type as ConfigItemType.ListConfigItem

        assertFalse(type.items.single { it.key == "sku" }.isNullable)
        assertTrue(type.items.single { it.key == "notify" }.isNullable)
        assertTrue(type.items.single { it.key == "maxPrice" }.isNullable)
    }

    @Test
    fun `createFrom accepts a nullable list`() {
        val item = createFrom(NullableListConfig::class).configurationItems.single()

        assertTrue(item.type is ConfigItemType.ListConfigItem)
        assertTrue(item.isNullable)
    }

    @Test
    fun `createFrom accepts two independent lists on one configuration`() {
        val items = createFrom(TwoListsConfig::class).configurationItems

        val targets = items.single { it.key == "targets" }.type as ConfigItemType.ListConfigItem
        val watches = items.single { it.key == "watches" }.type as ConfigItemType.ListConfigItem
        assertEquals(Target::class, targets.itemType)
        assertEquals(Watch::class, watches.itemType)
        assertEquals(listOf("url"), watches.items.map { it.key })
    }

    @Test
    fun `createFrom allows a record field to reuse a top-level item key`() {
        val items = createFrom(ReusedTopLevelKeyConfig::class).configurationItems

        val targets = items.single { it.key == "targets" }.type as ConfigItemType.ListConfigItem
        assertEquals(listOf("name"), targets.items.map { it.key })
    }

    @Test
    fun `createFrom rejects a list combined with valuePerTask`() {
        val exception =
            assertThrows<InvalidScriptConfigurationDefinitionException> {
                createFrom(PerTaskListConfig::class)
            }

        assertTrue(exception.message!!.contains("valuePerTask"), exception.message)
        assertTrue(exception.message!!.contains("Task data"), exception.message)
    }

    @Test
    fun `createFrom rejects a list marked as the script identifier`() {
        val exception =
            assertThrows<InvalidScriptConfigurationDefinitionException> {
                createFrom(IdentifierListConfig::class)
            }

        assertTrue(exception.message!!.contains("isScriptIdentifier"), exception.message)
    }

    @Test
    fun `createFrom rejects a default implementation on a list`() {
        val exception =
            assertThrows<InvalidScriptConfigurationDefinitionException> {
                createFrom(DefaultListConfig::class)
            }

        assertTrue(exception.message!!.contains("default"), exception.message)
    }

    @Test
    fun `createFrom rejects a state modifier on a field inside a record`() {
        val exception =
            assertThrows<InvalidScriptConfigurationDefinitionException> {
                createFrom(FieldStateModifierConfig::class)
            }

        assertTrue(exception.message!!.contains("stateModifier"), exception.message)
        assertTrue(exception.message!!.contains("targets"), exception.message)
    }

    @Test
    fun `createFrom rejects an unsupported field type inside a record`() {
        listOf(ProxyFieldConfig::class, NestedListFieldConfig::class, NestedRecordFieldConfig::class).forEach {
            val exception =
                assertThrows<InvalidScriptConfigurationDefinitionException>("Expected ${it.simpleName} to be rejected") {
                    createFrom(it)
                }

            assertTrue(exception.message!!.contains("targets"), exception.message)
        }
    }

    @Test
    fun `createFrom rejects duplicate field keys within one record`() {
        val exception =
            assertThrows<InvalidScriptConfigurationDefinitionException> {
                createFrom(DuplicateKeyConfig::class)
            }

        assertTrue(exception.message!!.contains("sku"), exception.message)
    }

    @Test
    fun `createFrom accepts a record declaring a single field`() {
        val item = createFrom(OneFieldConfig::class).configurationItems.single()

        val type = item.type as ConfigItemType.ListConfigItem
        assertEquals(Watch::class, type.itemType)
        assertEquals(listOf("url"), type.items.map { it.key })
    }

    @Test
    fun `createFrom rejects a List of String`() {
        val exception =
            assertThrows<UnsupportedConfigurationTypeException> {
                createFrom(StringListConfig::class)
            }

        assertTrue(exception.message!!.contains("tags"), exception.message)
        assertTrue(exception.message!!.contains("ScriptConfigurationListItem"), exception.message)
    }

    @Test
    fun `createFrom rejects a nullable List of String`() {
        assertThrows<UnsupportedConfigurationTypeException> {
            createFrom(NullableStringListConfig::class)
        }
    }

    @Test
    fun `createFrom rejects a List of any other non-record element type`() {
        listOf(IntListConfig::class, EnumListConfig::class).forEach {
            val exception =
                assertThrows<UnsupportedConfigurationTypeException>("Expected ${it.simpleName} to be rejected") {
                    createFrom(it)
                }

            assertTrue(exception.message!!.contains("ScriptConfigurationListItem"), exception.message)
        }
    }
}
