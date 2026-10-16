package com.cereal.client.fixtures

import com.cereal.client.application.task.TaskActionItem
import com.cereal.client.application.task.TaskActionRunner
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.sdk.ScriptConfiguration
import io.mockk.mockk
import java.io.File
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Stands in for script code: lists [actions] and runs them through [onRun] (by action name). */
class FakeTaskActionRunner(
    var actions: List<TaskActionItem> = emptyList(),
    var onRun: suspend (String) -> String? = { null },
) : TaskActionRunner {
    val runs = mutableListOf<String>()

    override fun actions(task: JobTask): List<TaskActionItem> = actions

    override suspend fun run(
        task: JobTask,
        actionName: String,
    ): String? {
        runs += actionName
        return onRun(actionName)
    }
}

/** A script package instance with one main-script task, as task actions need. */
@OptIn(ExperimentalTime::class)
fun mainTaskFixture(
    instanceId: String = "pkg-actions",
    statusHistory: List<TaskStatus> = emptyList(),
    scriptClass: KClass<out com.cereal.sdk.Script<*>> = com.cereal.sdk.Script::class,
): JobTask {
    val configDef = ScriptConfigurationDefinition(scriptConfigurationClass = ScriptConfiguration::class, configurationItems = emptyList())
    val scriptPackage =
        ScriptPackage(
            source = File("."),
            manifest = mockk(relaxed = true),
            mainScript = MainScript(clazz = scriptClass, configuration = configDef),
            childScripts = emptyMap(),
        )
    val packageInstance: ScriptPackageInstance =
        ScriptPackageInstance(
            id = instanceId,
            mainConfiguration = emptyMap(),
            childConfigurations = emptyMap(),
            definition = scriptPackage,
            createdAt = Clock.System.now(),
            numberOfConcurrentTasks = 1,
        )
    val scriptInstance =
        MainScriptInstance(
            id = "$instanceId-main",
            definition = scriptPackage.mainScript,
            configuration = emptyMap(),
            createdAt = Clock.System.now(),
            packageInstance = packageInstance,
        )
    return JobTask(
        id = "$instanceId-task",
        scriptInstance = scriptInstance,
        configuration = emptyMap(),
        statusHistory = statusHistory,
        createdAt = Clock.System.now(),
    )
}
