package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ScriptConcurrencyValidatorTest {
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
        defaultValue = null,
    )

    private fun definition(items: List<ScriptConfigurationItemDefinition>) =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = items,
        )

    private fun scriptPackage(
        mainItems: List<ScriptConfigurationItemDefinition> = emptyList(),
        childItems: Map<String, List<ScriptConfigurationItemDefinition>> = emptyMap(),
    ): ScriptPackage =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest = Manifest(packageName = "com.example", name = "Test", versionCode = 1L),
            mainScript = MainScript(clazz = Script::class, configuration = definition(mainItems)),
            childScripts =
                childItems.mapValues { (name, defs) ->
                    ChildScript(name = name, clazz = Script::class, configuration = definition(defs))
                },
        )

    private fun datasetGroupValue(numberOfItems: Int) =
        ConfigValue.CustomDatasetGroupValue(
            CustomDatasetGroup(
                id = "ds",
                name = "Dataset",
                numberOfItems = numberOfItems,
                itemDefinitions = emptyList(),
                items = emptySequence(),
                createdAt = Instant.fromEpochMilliseconds(0),
            ),
        )

    private fun proxyGroupValue(numberOfItems: Int) =
        ConfigValue.ProxyGroupValue(
            ProxyGroup(
                id = "px",
                name = "Proxies",
                numberOfItems = numberOfItems,
                items = emptySequence(),
            ),
        )

    @Test
    fun `validateTaskLimits returns null when there is no grouped configuration`() {
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage = scriptPackage(mainItems = listOf(itemDef("flag", ConfigItemType.BooleanConfigItem))),
                mainScriptConfiguration = emptyMap(),
                childScriptConfiguration = emptyMap(),
                numberOfConcurrentTasks = 5,
            )

        assertNull(validator.validateTaskLimits())
    }

    @Test
    fun `validateTaskLimits returns a conflict when the grouped dataset has fewer records than concurrent tasks`() {
        val grouped = itemDef("dataset", ConfigItemType.GroupedConfigItem(items = emptyList()))
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage = scriptPackage(mainItems = listOf(grouped)),
                mainScriptConfiguration = mapOf<String, ConfigValue>("dataset" to datasetGroupValue(numberOfItems = 2)),
                childScriptConfiguration = emptyMap(),
                numberOfConcurrentTasks = 5,
            )

        val conflict = validator.validateTaskLimits()

        assertEquals(2, conflict?.numberOfRecords)
        assertEquals("dataset", conflict?.definition?.key)
    }

    @Test
    fun `validateTaskLimits returns null when the grouped dataset has enough records`() {
        val grouped = itemDef("dataset", ConfigItemType.GroupedConfigItem(items = emptyList()))
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage = scriptPackage(mainItems = listOf(grouped)),
                mainScriptConfiguration = mapOf<String, ConfigValue>("dataset" to datasetGroupValue(numberOfItems = 10)),
                childScriptConfiguration = emptyMap(),
                numberOfConcurrentTasks = 5,
            )

        assertNull(validator.validateTaskLimits())
    }

    @Test
    fun `validateTaskLimits picks the conflict with the fewest records across main and child scripts`() {
        val mainGrouped = itemDef("dataset", ConfigItemType.GroupedConfigItem(items = emptyList()))
        val childGrouped = itemDef("childDataset", ConfigItemType.GroupedConfigItem(items = emptyList()))
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage =
                    scriptPackage(
                        mainItems = listOf(mainGrouped),
                        childItems = mapOf("child" to listOf(childGrouped)),
                    ),
                mainScriptConfiguration = mapOf<String, ConfigValue>("dataset" to datasetGroupValue(numberOfItems = 4)),
                childScriptConfiguration =
                    mapOf<String, ScriptConfigurationValues>(
                        "childDataset" to mapOf("childDataset" to datasetGroupValue(numberOfItems = 1)),
                    ),
                numberOfConcurrentTasks = 5,
            )

        val conflict = validator.validateTaskLimits()

        assertEquals(1, conflict?.numberOfRecords)
        assertEquals("childDataset", conflict?.definition?.key)
    }

    @Test
    fun `validateRecordsReused returns conflicts when a proxy group has fewer proxies than concurrent tasks`() {
        val proxy = itemDef("proxy", ConfigItemType.ProxyConfigItem)
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage = scriptPackage(mainItems = listOf(proxy)),
                mainScriptConfiguration = mapOf<String, ConfigValue>("proxy" to proxyGroupValue(numberOfItems = 1)),
                childScriptConfiguration = emptyMap(),
                numberOfConcurrentTasks = 3,
            )

        val conflicts = validator.validateRecordsReused()

        assertEquals(1, conflicts.size)
        assertEquals(1, conflicts.first().numberOfRecords)
    }

    @Test
    fun `validateRecordsReused is empty when there is no proxy configuration`() {
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage = scriptPackage(mainItems = listOf(itemDef("flag", ConfigItemType.BooleanConfigItem))),
                mainScriptConfiguration = emptyMap(),
                childScriptConfiguration = emptyMap(),
                numberOfConcurrentTasks = 3,
            )

        assertTrue(validator.validateRecordsReused().isEmpty())
    }

    @Test
    fun `validateRecordsReused includes a child script proxy conflict`() {
        val childProxy = itemDef("childProxy", ConfigItemType.ProxyConfigItem)
        val validator =
            ScriptConcurrencyValidator(
                scriptPackage = scriptPackage(childItems = mapOf("child" to listOf(childProxy))),
                mainScriptConfiguration = emptyMap(),
                childScriptConfiguration =
                    mapOf<String, ScriptConfigurationValues>(
                        "childProxy" to mapOf("childProxy" to proxyGroupValue(numberOfItems = 0)),
                    ),
                numberOfConcurrentTasks = 2,
            )

        val conflicts = validator.validateRecordsReused()

        assertEquals(1, conflicts.size)
        assertEquals(0, conflicts.first().numberOfRecords)
    }
}
