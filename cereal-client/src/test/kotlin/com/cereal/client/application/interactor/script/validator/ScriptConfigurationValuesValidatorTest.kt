package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ScriptConfigurationValuesValidatorTest {
    private fun definition(items: List<ScriptConfigurationItemDefinition>) =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = items,
        )

    private fun requiredStringItem(key: String) =
        ScriptConfigurationItemDefinition(
            name = key,
            description = "desc-$key",
            key = key,
            position = 0,
            type = ConfigItemType.StringConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
            defaultValue = null,
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

    @Test
    fun `validate passes when main config is valid and there are no child scripts`() {
        val validator =
            ScriptConfigurationValuesValidator(
                scriptPackage = scriptPackage(),
                mainScriptConfiguration = emptyMap(),
                childConfigurations = emptyMap(),
            )

        assertDoesNotThrow { validator.validate() }
    }

    @Test
    fun `validate throws when a required main config value is missing`() {
        val validator =
            ScriptConfigurationValuesValidator(
                scriptPackage = scriptPackage(mainItems = listOf(requiredStringItem("token"))),
                mainScriptConfiguration = emptyMap(),
                childConfigurations = emptyMap(),
            )

        assertThrows<InvalidScriptConfigurationException> { validator.validate() }
    }

    @Test
    fun `validate passes when a child script has matching valid configuration`() {
        val validator =
            ScriptConfigurationValuesValidator(
                scriptPackage = scriptPackage(childItems = mapOf("child" to emptyList())),
                mainScriptConfiguration = emptyMap(),
                childConfigurations = mapOf<String, ScriptConfigurationValues>("child" to emptyMap()),
            )

        assertDoesNotThrow { validator.validate() }
    }

    @Test
    fun `validate throws when a child script has no matching configuration`() {
        val validator =
            ScriptConfigurationValuesValidator(
                scriptPackage = scriptPackage(childItems = mapOf("child" to emptyList())),
                mainScriptConfiguration = emptyMap(),
                childConfigurations = emptyMap(),
            )

        assertThrows<InvalidScriptConfigurationException> { validator.validate() }
    }
}
