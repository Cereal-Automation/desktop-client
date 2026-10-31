package fixtures

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.ScriptVariant
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Builders for the real domain objects used by the in-memory repositories in tests.
 *
 * These construct genuine [JobTask]/[ScriptPackageInstance]/[ScriptInstance] values (no MockK)
 * so tests can seed [com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository]
 * and assert on observable behaviour rather than mocked accessors.
 */
@OptIn(ExperimentalTime::class)
fun aScriptPackage(packageName: String): ScriptPackage {
    val configDef =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = emptyList(),
        )
    return ScriptPackage(
        source = File("/tmp/fake.jar"),
        manifest =
            Manifest(
                packageName = packageName,
                name = "Test",
                versionCode = 1L,
            ),
        mainScript = MainScript(clazz = Script::class, configuration = configDef),
        childScripts = emptyMap(),
    )
}

/**
 * A package with one variant, [variantId], whose configuration has a single text field keyed `variantField`
 * (the default entry has no fields), so a test can tell the two configuration forms apart.
 */
fun aScriptPackageWithVariant(
    packageName: String,
    variantId: String = "variant",
    variantName: String = "Variant",
): ScriptPackage {
    val variantConfiguration =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems =
                listOf(
                    ScriptConfigurationItemDefinition(
                        name = "Variant field",
                        description = "",
                        key = VARIANT_FIELD,
                        position = 0,
                        type = ConfigItemType.StringConfigItem,
                        isNullable = true,
                        stateModifier = null,
                        isScriptIdentifier = false,
                        defaultValue = null,
                    ),
                ),
        )
    return aScriptPackage(packageName).copy(
        variants =
            mapOf(
                variantId to
                    ScriptVariant(
                        name = variantName,
                        instructions = null,
                        script = MainScript(clazz = Script::class, configuration = variantConfiguration),
                    ),
            ),
    )
}

const val VARIANT_FIELD = "variantField"

@OptIn(ExperimentalTime::class)
fun aScriptPackageInstance(
    id: String,
    packageName: String,
    definition: ScriptPackage = aScriptPackage(packageName),
    variantId: String? = null,
): ScriptPackageInstance =
    ScriptPackageInstance(
        id = id,
        mainConfiguration = emptyMap(),
        childConfigurations = emptyMap(),
        definition = definition,
        createdAt = Clock.System.now(),
        numberOfConcurrentTasks = 1,
        variantId = variantId,
    )

@OptIn(ExperimentalTime::class)
fun aScriptInstance(packageInstance: ScriptPackageInstance): MainScriptInstance =
    MainScriptInstance(
        id = "script-${packageInstance.id}",
        definition = packageInstance.definition.mainScript!!,
        configuration = emptyMap(),
        createdAt = Clock.System.now(),
        packageInstance = packageInstance,
    )

@OptIn(ExperimentalTime::class)
fun aTask(
    id: String,
    scriptInstance: ScriptInstance,
    running: Boolean,
): JobTask =
    JobTask(
        id = id,
        scriptInstance = scriptInstance,
        configuration = emptyMap(),
        statusHistory =
            listOf(
                if (running) {
                    TaskStatus.Running(timestamp = Clock.System.now())
                } else {
                    TaskStatus.Idle(timestamp = Clock.System.now())
                },
            ),
        createdAt = Clock.System.now(),
    )

@OptIn(ExperimentalTime::class)
fun aTask(
    id: String,
    packageName: String,
    running: Boolean,
): JobTask = aTask(id, aScriptInstance(aScriptPackageInstance("pkg-$id", packageName)), running)

/** This instance with a script identifier item on its entry script (main or variant) set to [identifier]. */
fun ScriptPackageInstance.withIdentifier(identifier: String?): ScriptPackageInstance {
    val item =
        ScriptConfigurationItemDefinition(
            name = "Id",
            description = "",
            key = IDENTIFIER_FIELD,
            position = 0,
            type = ConfigItemType.StringConfigItem,
            isNullable = true,
            stateModifier = null,
            isScriptIdentifier = true,
        )

    fun MainScript.identified() = copy(configuration = configuration.copy(configurationItems = listOf(item)))
    val definition =
        if (variantId == null) {
            definition.copy(mainScript = definition.mainScript!!.identified())
        } else {
            definition.copy(
                variants = definition.variants.mapValues { (id, v) -> if (id == variantId) v.copy(script = v.script.identified()) else v },
            )
        }
    return copy(
        definition = definition,
        mainConfiguration = identifier?.let { mapOf(IDENTIFIER_FIELD to ConfigValue.StringValue(it)) }.orEmpty(),
    )
}

private const val IDENTIFIER_FIELD = "id"
