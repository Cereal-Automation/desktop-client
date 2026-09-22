package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
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
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Unit tests for [CapacityValidator.runRecordCount] — the run-wide record figure capacity enforcement
 * compares against the entitled cap. Pins the documented aggregation rule: the count is the **main**
 * script's grouped dataset size, `0` when there is none, and child datasets are excluded.
 */
@OptIn(ExperimentalTime::class)
class CapacityValidatorTest {
    @Test
    fun `runRecordCount is the main grouped dataset size`() {
        val validator =
            CapacityValidator(
                scriptPackage = scriptPackage(mainItems = listOf(groupedItemDef("dataset"))),
                mainScriptConfiguration = mapOf("dataset" to datasetGroupValue(numberOfItems = 42)),
            )

        assertEquals(42, validator.runRecordCount())
    }

    @Test
    fun `runRecordCount is zero when the main script has no grouped dataset`() {
        val validator =
            CapacityValidator(
                scriptPackage = scriptPackage(mainItems = listOf(itemDef("flag", ConfigItemType.BooleanConfigItem))),
                mainScriptConfiguration = emptyMap(),
            )

        assertEquals(0, validator.runRecordCount())
    }

    @Test
    fun `runRecordCount is zero when the grouped dataset has no configured value`() {
        // The definition exists but the run provided no value for it.
        val validator =
            CapacityValidator(
                scriptPackage = scriptPackage(mainItems = listOf(groupedItemDef("dataset"))),
                mainScriptConfiguration = emptyMap(),
            )

        assertEquals(0, validator.runRecordCount())
    }

    @Test
    fun `runRecordCount reflects only the main dataset and excludes child datasets`() {
        // A package with both a main and a child grouped dataset: only the main count is used.
        val validator =
            CapacityValidator(
                scriptPackage =
                    scriptPackage(
                        mainItems = listOf(groupedItemDef("dataset")),
                        childItems = mapOf("child" to listOf(groupedItemDef("childDataset"))),
                    ),
                mainScriptConfiguration = mapOf("dataset" to datasetGroupValue(numberOfItems = 10)),
            )

        assertEquals(10, validator.runRecordCount())
    }

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

    private fun groupedItemDef(key: String) = itemDef(key, ConfigItemType.GroupedConfigItem(items = emptyList()))

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
}
