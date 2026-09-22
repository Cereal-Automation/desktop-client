package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.sdk.ScriptConfiguration
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class InMemoryTasksRepositoryTest {
    private val configDef =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = emptyList(),
        )

    private val scriptPackage =
        ScriptPackage(
            source = File("."),
            manifest = mockk(relaxed = true),
            mainScript = MainScript(clazz = com.cereal.sdk.Script::class, configuration = configDef),
            childScripts = emptyMap(),
        )

    private fun packageInstance(id: String) =
        ScriptPackageInstance(
            id = id,
            mainConfiguration = emptyMap(),
            childConfigurations = emptyMap(),
            definition = scriptPackage,
            createdAt = Clock.System.now(),
            numberOfConcurrentTasks = 1,
        )

    private fun task(
        taskId: String,
        scriptInstanceId: String,
        packageInstance: ScriptPackageInstance,
    ) = JobTask(
        id = taskId,
        scriptInstance =
            MainScriptInstance(
                id = scriptInstanceId,
                definition = scriptPackage.mainScript,
                configuration = emptyMap(),
                createdAt = Clock.System.now(),
                packageInstance = packageInstance,
            ),
        configuration = emptyMap(),
        createdAt = Clock.System.now(),
    )

    @Test
    fun `getTasks returns only tasks whose script instance belongs to the package instance`() =
        runTest {
            val repository = InMemoryTasksRepository()
            val pkgA = packageInstance("pkg-a")
            val pkgB = packageInstance("pkg-b")
            repository.addTask(task("task-a", "script-a", pkgA))
            repository.addTask(task("task-b", "script-b", pkgB))

            val result = repository.getTasks(pkgA)

            assertEquals(listOf("task-a"), result.map { it.id })
        }

    @Test
    fun `getTasksFlow emits only tasks whose script instance belongs to the package instance`() =
        runTest {
            val repository = InMemoryTasksRepository()
            val pkgA = packageInstance("pkg-a")
            val pkgB = packageInstance("pkg-b")
            repository.addTask(task("task-a", "script-a", pkgA))
            repository.addTask(task("task-b", "script-b", pkgB))

            val result = repository.getTasksFlow(pkgB).first()

            assertEquals(listOf("task-b"), result.map { it.id })
        }
}
