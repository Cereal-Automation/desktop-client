package com.cereal.client.application.interactor.script

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.proxy.ProxyGroup
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Edge cases for the proxy- and dataset-group branches of [GetScriptConfigDefinitionInteractor] that the
 * existing suites ([GetScriptConfigDefinitionInteractorAdditionalTest], [GetScriptConfigDefinitionInteractorListTest])
 * do not cover:
 *
 *  - the [ConfigItemType.ProxyGroupConfigItem] arm of the shared proxy branch (the existing test only drives
 *    [ConfigItemType.ProxyConfigItem]);
 *  - empty-repository behaviour (no proxy groups / no dataset groups → empty `options`);
 *  - a saved value referencing a group that the repository no longer contains (value preserved, group absent
 *    from `options`);
 *  - the dataset branch reading its saved value from the fixed [ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET]
 *    key rather than the item's own key.
 *
 * ScriptPackage/MainScript are relaxed MockK fixtures (mirroring the existing suites); the proxy and dataset
 * repositories are real in-memory implementations.
 */
@OptIn(ExperimentalTime::class)
class GetScriptConfigDefinitionInteractorGroupEdgeCasesTest {
    private fun itemDef(
        key: String,
        type: ConfigItemType,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc-$key",
        key = key,
        position = 0,
        type = type,
        isNullable = false,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    private fun scriptPackage(mainItems: List<ScriptConfigurationItemDefinition>): ScriptPackage {
        val mainScript = mockk<MainScript>(relaxed = true)
        every { mainScript.configuration } returns
            ScriptConfigurationDefinition(
                scriptConfigurationClass = mockk(relaxed = true),
                configurationItems = mainItems,
            )

        val scriptPackage = mockk<ScriptPackage>(relaxed = true)
        every { scriptPackage.mainScript } returns mainScript
        every { scriptPackage.childScripts } returns emptyMap()
        return scriptPackage
    }

    private fun instance(mainConfiguration: Map<String, ConfigValue> = emptyMap()): ScriptPackageInstance {
        val scriptInstance = mockk<ScriptPackageInstance>(relaxed = true)
        every { scriptInstance.mainConfiguration } returns mainConfiguration
        every { scriptInstance.childConfigurations } returns emptyMap()
        return scriptInstance
    }

    private fun proxyGroup(id: String) =
        ProxyGroup(
            id = id,
            name = "Proxy $id",
            numberOfItems = 0,
            items = emptySequence(),
        )

    private fun datasetGroup(id: String) =
        CustomDatasetGroup(
            id = id,
            name = "Dataset $id",
            numberOfItems = 0,
            itemDefinitions = emptyList(),
            items = emptySequence(),
            createdAt = Clock.System.now(),
        )

    @Test
    fun `run maps ProxyGroupConfigItem using proxy groups from the repository`() =
        runTest {
            val group = proxyGroup("pg-1")
            val proxyRepository = InMemoryProxyRepository(initialGroups = listOf(group))
            val interactor =
                GetScriptConfigDefinitionInteractor(proxyRepository, InMemoryCustomDatasetRepository())

            val scriptPackage =
                scriptPackage(listOf(itemDef("proxyGroup", ConfigItemType.ProxyGroupConfigItem)))
            val scriptInstance =
                instance(mapOf("proxyGroup" to ConfigValue.ProxyGroupValue(group)))

            val item = interactor.run(params(scriptPackage, scriptInstance)).mainConfiguration.single()

            assertInstanceOf(ConfigItemType.ProxyGroupConfigItem::class.java, item.definition.type)
            assertEquals(listOf(ConfigValue.ProxyGroupValue(group)), item.options)
            assertEquals(ConfigValue.ProxyGroupValue(group), item.value)
        }

    @Test
    fun `run produces empty proxy options when the repository has no proxy groups`() =
        runTest {
            val interactor =
                GetScriptConfigDefinitionInteractor(
                    InMemoryProxyRepository(),
                    InMemoryCustomDatasetRepository(),
                )

            val scriptPackage = scriptPackage(listOf(itemDef("proxy", ConfigItemType.ProxyConfigItem)))

            val item = interactor.run(params(scriptPackage, null)).mainConfiguration.single()

            assertInstanceOf(ConfigItemType.ProxyConfigItem::class.java, item.definition.type)
            assertTrue(item.options.isEmpty())
            assertNull(item.value)
        }

    @Test
    fun `run produces empty dataset options when the repository has no dataset groups`() =
        runTest {
            val interactor =
                GetScriptConfigDefinitionInteractor(
                    InMemoryProxyRepository(),
                    InMemoryCustomDatasetRepository(),
                )

            val scriptPackage =
                scriptPackage(listOf(itemDef("dataset", ConfigItemType.GroupedConfigItem(items = emptyList()))))

            val item = interactor.run(params(scriptPackage, null)).mainConfiguration.single()

            assertInstanceOf(ConfigItemType.GroupedConfigItem::class.java, item.definition.type)
            assertTrue(item.options.isEmpty())
            assertNull(item.value)
        }

    @Test
    fun `run preserves a saved proxy group value even when it is absent from the repository options`() =
        runTest {
            val availableGroup = proxyGroup("pg-available")
            // The instance points at a group that is no longer in the repository (e.g. it was deleted).
            val staleGroup = proxyGroup("pg-stale")
            val proxyRepository = InMemoryProxyRepository(initialGroups = listOf(availableGroup))
            val interactor =
                GetScriptConfigDefinitionInteractor(proxyRepository, InMemoryCustomDatasetRepository())

            val scriptPackage = scriptPackage(listOf(itemDef("proxy", ConfigItemType.ProxyConfigItem)))
            val scriptInstance = instance(mapOf("proxy" to ConfigValue.ProxyGroupValue(staleGroup)))

            val item = interactor.run(params(scriptPackage, scriptInstance)).mainConfiguration.single()

            // options reflect what the repository currently has; the saved (stale) value is still surfaced.
            assertEquals(listOf(ConfigValue.ProxyGroupValue(availableGroup)), item.options)
            assertEquals(ConfigValue.ProxyGroupValue(staleGroup), item.value)
            assertTrue(item.options.none { (it as ConfigValue.ProxyGroupValue).raw.id == staleGroup.id })
        }

    @Test
    fun `run reads the dataset value from the custom dataset key rather than the item key`() =
        runTest {
            val group = datasetGroup("ds-1")
            val customDatasetRepository = InMemoryCustomDatasetRepository(initialGroups = listOf(group))
            val interactor =
                GetScriptConfigDefinitionInteractor(InMemoryProxyRepository(), customDatasetRepository)

            // The item key deliberately differs from KEY_CUSTOM_DATASET: the grouped branch must look the
            // saved value up under the fixed custom-dataset key, not under the item's own key.
            val scriptPackage =
                scriptPackage(listOf(itemDef("myDatasetField", ConfigItemType.GroupedConfigItem(items = emptyList()))))
            val scriptInstance =
                instance(
                    mapOf(
                        // A value stored under the item's own key must be ignored by the grouped branch.
                        "myDatasetField" to ConfigValue.CustomDatasetGroupValue(datasetGroup("ds-wrong")),
                        ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key to
                            ConfigValue.CustomDatasetGroupValue(group),
                    ),
                )

            val item = interactor.run(params(scriptPackage, scriptInstance)).mainConfiguration.single()

            assertEquals(ConfigValue.CustomDatasetGroupValue(group), item.value)
            assertEquals(listOf(ConfigValue.CustomDatasetGroupValue(group)), item.options)
        }

    @Test
    fun `run produces null dataset value when the custom dataset key is not set`() =
        runTest {
            val group = datasetGroup("ds-1")
            val customDatasetRepository = InMemoryCustomDatasetRepository(initialGroups = listOf(group))
            val interactor =
                GetScriptConfigDefinitionInteractor(InMemoryProxyRepository(), customDatasetRepository)

            val scriptPackage =
                scriptPackage(listOf(itemDef("dataset", ConfigItemType.GroupedConfigItem(items = emptyList()))))
            // Instance has no value under KEY_CUSTOM_DATASET, so the dataset value resolves to null while
            // the options still reflect the repository contents.
            val scriptInstance = instance(emptyMap())

            val item = interactor.run(params(scriptPackage, scriptInstance)).mainConfiguration.single()

            assertNull(item.value)
            assertEquals(listOf(ConfigValue.CustomDatasetGroupValue(group)), item.options)
        }

    private fun params(
        scriptPackage: ScriptPackage,
        scriptPackageInstance: ScriptPackageInstance?,
    ) = GetScriptConfigDefinitionInteractor.Params(
        scriptPackage = scriptPackage,
        scriptPackageInstance = scriptPackageInstance,
    )
}
