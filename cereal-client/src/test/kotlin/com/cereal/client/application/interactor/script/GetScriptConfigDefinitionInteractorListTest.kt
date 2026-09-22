package com.cereal.client.application.interactor.script

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class GetScriptConfigDefinitionInteractorListTest {
    // Both repositories are real in-memory implementations (empty); the list path under test never
    // reads proxy/dataset groups. ScriptPackage/MainScript stay domain-model fixtures.
    private val proxyRepository = InMemoryProxyRepository()
    private val customDatasetRepository = InMemoryCustomDatasetRepository()
    private val interactor = GetScriptConfigDefinitionInteractor(proxyRepository, customDatasetRepository)

    private interface Tag

    private val tagField =
        ScriptConfigurationItemDefinition(
            name = "Tag",
            description = "One tag",
            key = "tag",
            position = 0,
            type = ConfigItemType.StringConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private val tagsItem =
        ScriptConfigurationItemDefinition(
            name = "Tags",
            description = "Tag list",
            key = "tags",
            position = 0,
            type = ConfigItemType.ListConfigItem(itemType = Tag::class, items = listOf(tagField)),
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    @Test
    fun `run should produce a list item when type is ListConfigItem`() =
        runTest {
            val result = interactor.run(paramsFor(instance = null))

            val item = result.mainConfiguration.single()
            assertInstanceOf(ConfigItemType.ListConfigItem::class.java, item.definition.type)
            assertEquals(null, item.value)
        }

    @Test
    fun `run should restore saved list value when present in configuration values`() =
        runTest {
            val savedRows = ListRows(listOf(ListRow(mapOf("tag" to ConfigValue.StringValue("alpha")))))
            val scriptInstance = mockk<ScriptPackageInstance>(relaxed = true)
            every { scriptInstance.mainConfiguration } returns mapOf("tags" to ConfigValue.ListValue(savedRows))

            val result = interactor.run(paramsFor(instance = scriptInstance))

            val item = result.mainConfiguration.single()
            assertEquals(ConfigValue.ListValue(savedRows), item.value)
        }

    private fun paramsFor(instance: ScriptPackageInstance?): GetScriptConfigDefinitionInteractor.Params {
        val configDef =
            ScriptConfigurationDefinition(
                scriptConfigurationClass = mockk(relaxed = true),
                configurationItems = listOf(tagsItem),
            )
        val mainScript = mockk<MainScript>(relaxed = true)
        val scriptPackage = mockk<ScriptPackage>(relaxed = true)

        every { mainScript.configuration } returns configDef
        every { scriptPackage.mainScript } returns mainScript
        every { scriptPackage.childScripts } returns emptyMap()

        return GetScriptConfigDefinitionInteractor.Params(
            scriptPackage = scriptPackage,
            scriptPackageInstance = instance,
        )
    }
}
