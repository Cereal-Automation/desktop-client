package com.cereal.client.application.interactor.script

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Covers the configuration-item branches not exercised by [GetScriptConfigDefinitionInteractorListTest]:
 * the scalar types (Boolean/Int/Float/Double/String/Enum), the proxy- and dataset-group items (which read the
 * real in-memory repositories), the default-value fallback, and child-script configurations.
 *
 * ScriptPackage/MainScript are relaxed MockK fixtures (mirroring the existing list test); the proxy and
 * dataset repositories are real in-memory implementations seeded with genuine groups.
 */
class GetScriptConfigDefinitionInteractorAdditionalTest {
    private enum class SampleColor { RED, GREEN }

    private fun itemDef(
        key: String,
        type: ConfigItemType,
        defaultValue: ConfigValue? = null,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc-$key",
        key = key,
        position = 0,
        type = type,
        isNullable = false,
        stateModifier = null,
        isScriptIdentifier = false,
        defaultValue = defaultValue,
    )

    private fun scriptPackage(
        mainItems: List<ScriptConfigurationItemDefinition>,
        childItems: Map<String, List<ScriptConfigurationItemDefinition>> = emptyMap(),
    ): ScriptPackage {
        val mainScript = mockk<MainScript>(relaxed = true)
        every { mainScript.configuration } returns
            ScriptConfigurationDefinition(
                scriptConfigurationClass = mockk(relaxed = true),
                configurationItems = mainItems,
            )

        val childScripts =
            childItems.mapValues { (_, defs) ->
                val childScript = mockk<ChildScript>(relaxed = true)
                every { childScript.configuration } returns
                    ScriptConfigurationDefinition(
                        scriptConfigurationClass = mockk(relaxed = true),
                        configurationItems = defs,
                    )
                childScript
            }

        val scriptPackage = mockk<ScriptPackage>(relaxed = true)
        every { scriptPackage.mainScript } returns mainScript
        every { scriptPackage.childScripts } returns childScripts
        return scriptPackage
    }

    private fun instance(
        mainConfiguration: Map<String, ConfigValue> = emptyMap(),
        childConfigurations: Map<String, Map<String, ConfigValue>> = emptyMap(),
    ): ScriptPackageInstance {
        val scriptInstance = mockk<ScriptPackageInstance>(relaxed = true)
        every { scriptInstance.mainConfiguration } returns mainConfiguration
        every { scriptInstance.childConfigurations } returns childConfigurations
        return scriptInstance
    }

    @Test
    fun `run maps each scalar config item type to its matching configuration item`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage =
                scriptPackage(
                    mainItems =
                        listOf(
                            itemDef("flag", ConfigItemType.BooleanConfigItem),
                            itemDef("count", ConfigItemType.IntConfigItem),
                            itemDef("ratio", ConfigItemType.FloatConfigItem),
                            itemDef("precise", ConfigItemType.DoubleConfigItem),
                            itemDef("label", ConfigItemType.StringConfigItem),
                        ),
                )
            val scriptInstance =
                instance(
                    mainConfiguration =
                        mapOf(
                            "flag" to ConfigValue.BooleanValue(true),
                            "count" to ConfigValue.IntValue(7),
                            "ratio" to ConfigValue.FloatValue(1.5f),
                            "precise" to ConfigValue.DoubleValue(2.5),
                            "label" to ConfigValue.StringValue("hello"),
                        ),
                )

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = scriptInstance,
                    ),
                )

            val items = result.mainConfiguration
            assertEquals(ConfigValue.BooleanValue(true), items[0].value)
            assertEquals(ConfigValue.IntValue(7), items[1].value)
            assertEquals(ConfigValue.FloatValue(1.5f), items[2].value)
            assertEquals(ConfigValue.DoubleValue(2.5), items[3].value)
            assertEquals(ConfigValue.StringValue("hello"), items[4].value)
        }

    @Test
    fun `run falls back to the default value when the instance has no saved value`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage =
                scriptPackage(
                    mainItems =
                        listOf(
                            itemDef(
                                key = "count",
                                type = ConfigItemType.IntConfigItem,
                                defaultValue = ConfigValue.IntValue(42),
                            ),
                        ),
                )

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = null,
                    ),
                )

            assertEquals(ConfigValue.IntValue(42), result.mainConfiguration.single().value)
        }

    @Test
    fun `run produces null scalar value when neither saved value nor default is present`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage =
                scriptPackage(mainItems = listOf(itemDef("label", ConfigItemType.StringConfigItem)))

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = null,
                    ),
                )

            assertNull(result.mainConfiguration.single().value)
        }

    @Test
    fun `run maps an enum config item including its options and saved value`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            @Suppress("UNCHECKED_CAST")
            val enumType = SampleColor::class as KClass<Enum<*>>
            val scriptPackage =
                scriptPackage(
                    mainItems = listOf(itemDef("color", ConfigItemType.EnumConfigItem(enumType))),
                )
            val scriptInstance =
                instance(mainConfiguration = mapOf("color" to ConfigValue.EnumValue(SampleColor.GREEN)))

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = scriptInstance,
                    ),
                )

            val item = result.mainConfiguration.single()
            assertInstanceOf(ConfigItemType.EnumConfigItem::class.java, item.definition.type)
            assertEquals(ConfigValue.EnumValue(SampleColor.GREEN), item.value)
            assertEquals(
                listOf(ConfigValue.EnumValue(SampleColor.RED), ConfigValue.EnumValue(SampleColor.GREEN)),
                item.options,
            )
        }

    @Test
    fun `run maps proxy config item with proxy groups from the repository`() =
        runTest {
            val proxyGroup =
                ProxyGroup(
                    id = "pg-1",
                    name = "Group One",
                    numberOfItems = 0,
                    items = emptySequence(),
                )
            val proxyRepository = InMemoryProxyRepository(initialGroups = listOf(proxyGroup))
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage =
                scriptPackage(mainItems = listOf(itemDef("proxy", ConfigItemType.ProxyConfigItem)))
            val scriptInstance =
                instance(mainConfiguration = mapOf("proxy" to ConfigValue.ProxyGroupValue(proxyGroup)))

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = scriptInstance,
                    ),
                )

            val item = result.mainConfiguration.single()
            assertInstanceOf(ConfigItemType.ProxyConfigItem::class.java, item.definition.type)
            assertEquals(listOf(ConfigValue.ProxyGroupValue(proxyGroup)), item.options)
            assertEquals(ConfigValue.ProxyGroupValue(proxyGroup), item.value)
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `run maps grouped config item with dataset groups from the repository`() =
        runTest {
            val datasetGroup =
                CustomDatasetGroup(
                    id = "ds-1",
                    name = "Dataset One",
                    numberOfItems = 0,
                    itemDefinitions = emptyList(),
                    items = emptySequence(),
                    createdAt = Clock.System.now(),
                )
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository(initialGroups = listOf(datasetGroup))
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage =
                scriptPackage(
                    mainItems = listOf(itemDef("dataset", ConfigItemType.GroupedConfigItem(items = emptyList()))),
                )
            val scriptInstance =
                instance(
                    mainConfiguration =
                        mapOf(
                            ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key to
                                ConfigValue.CustomDatasetGroupValue(datasetGroup),
                        ),
                )

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = scriptInstance,
                    ),
                )

            val item = result.mainConfiguration.single()
            assertInstanceOf(ConfigItemType.GroupedConfigItem::class.java, item.definition.type)
            assertEquals(listOf(ConfigValue.CustomDatasetGroupValue(datasetGroup)), item.options)
            assertEquals(ConfigValue.CustomDatasetGroupValue(datasetGroup), item.value)
        }

    @Test
    fun `run builds configuration items for child scripts keyed by child name`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage =
                scriptPackage(
                    mainItems = listOf(itemDef("main", ConfigItemType.StringConfigItem)),
                    childItems = mapOf("child-a" to listOf(itemDef("childFlag", ConfigItemType.BooleanConfigItem))),
                )
            val scriptInstance =
                instance(
                    mainConfiguration = mapOf("main" to ConfigValue.StringValue("m")),
                    childConfigurations = mapOf("child-a" to mapOf("childFlag" to ConfigValue.BooleanValue(true))),
                )

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = scriptInstance,
                    ),
                )

            assertEquals(setOf("child-a"), result.childConfigurations.keys)
            val childItem = result.childConfigurations.getValue("child-a").single()
            assertInstanceOf(ConfigItemType.BooleanConfigItem::class.java, childItem.definition.type)
            assertEquals(ConfigValue.BooleanValue(true), childItem.value)
            assertEquals(ConfigValue.StringValue("m"), result.mainConfiguration.single().value)
        }

    @Test
    fun `run uses default value when saved value type does not match the item type`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            // Saved value is a StringValue but the item is an Int: the as? cast yields null.
            val scriptPackage =
                scriptPackage(mainItems = listOf(itemDef("count", ConfigItemType.IntConfigItem)))
            val scriptInstance =
                instance(mainConfiguration = mapOf("count" to ConfigValue.StringValue("not-an-int")))

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = scriptInstance,
                    ),
                )

            assertNull(result.mainConfiguration.single().value)
        }

    @Test
    fun `run returns empty configurations when the main script has no items`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val customDatasetRepository = InMemoryCustomDatasetRepository()
            val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

            val scriptPackage = scriptPackage(mainItems = emptyList())

            val result =
                interactor.run(
                    GetScriptConfigDefinitionInteractor.Params(
                        scriptPackage = scriptPackage,
                        scriptPackageInstance = null,
                    ),
                )

            assertTrue(result.mainConfiguration.isEmpty())
            assertTrue(result.childConfigurations.isEmpty())
        }
}
