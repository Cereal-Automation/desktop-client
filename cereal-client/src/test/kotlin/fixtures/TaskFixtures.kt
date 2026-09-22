package fixtures

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
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

@OptIn(ExperimentalTime::class)
fun aScriptPackageInstance(
    id: String,
    packageName: String,
): ScriptPackageInstance =
    ScriptPackageInstance(
        id = id,
        mainConfiguration = emptyMap(),
        childConfigurations = emptyMap(),
        definition = aScriptPackage(packageName),
        createdAt = Clock.System.now(),
        numberOfConcurrentTasks = 1,
    )

@OptIn(ExperimentalTime::class)
fun aScriptInstance(packageInstance: ScriptPackageInstance): MainScriptInstance =
    MainScriptInstance(
        id = "script-${packageInstance.id}",
        definition = packageInstance.definition.mainScript,
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
