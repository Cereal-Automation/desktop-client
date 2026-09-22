package com.cereal.client.application.interactor.task

import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StartAllTasksInScriptPackageInstanceInteractorTest {
    // TaskManager has no observable state of its own; we mock it (relaxed) and assert the
    // interactor delegates start-to-concurrency-limit once per script instance.
    private val taskManager = mockk<TaskManager>(relaxed = true)

    /**
     * The shared [InMemoryScriptInstanceRepository.getScriptInstances] always returns empty, which
     * would make the happy path indistinguishable from a no-op. We delegate to it and return a
     * caller-provided set of script instances instead.
     */
    private class FixedScriptInstancesRepository(
        private val scriptInstances: List<ScriptInstance>,
        private val delegate: InMemoryScriptInstanceRepository = InMemoryScriptInstanceRepository(),
    ) : ScriptInstanceRepository by delegate {
        override suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance> = scriptInstances
    }

    @Test
    fun `run should start tasks to the concurrency limit for every script instance`() =
        runTest {
            val packageInstance = aScriptPackageInstance("instance-1", "com.example.one")
            val main = aScriptInstance(packageInstance)
            val repository = FixedScriptInstancesRepository(listOf(main))
            val interactor = StartAllTasksInScriptPackageInstanceInteractor(taskManager, repository)

            interactor.run(StartAllTasksInScriptPackageInstanceInteractor.Params(packageInstance))

            coVerify(exactly = 1) { taskManager.startTasksToConcurrencyLimit(main) }
        }

    @Test
    fun `run should throw and not start any tasks when the configuration is invalid`() =
        runTest {
            // A child script with no matching child configuration makes hasValidConfiguration() false.
            val base = aScriptPackageInstance("instance-1", "com.example.one")
            val childScript =
                ChildScript(
                    name = "child",
                    clazz = base.definition.mainScript.clazz,
                    configuration = base.definition.mainScript.configuration,
                )
            val invalidInstance =
                base.copy(
                    definition = base.definition.copy(childScripts = mapOf("child" to childScript)),
                    childConfigurations = emptyMap(),
                )
            val repository = FixedScriptInstancesRepository(listOf(aScriptInstance(base)))
            val interactor = StartAllTasksInScriptPackageInstanceInteractor(taskManager, repository)

            assertThrows<InvalidScriptConfigurationException> {
                interactor.run(StartAllTasksInScriptPackageInstanceInteractor.Params(invalidInstance))
            }

            coVerify(exactly = 0) { taskManager.startTasksToConcurrencyLimit(any()) }
        }

    @Test
    fun `run should start nothing when the instance has no script instances`() =
        runTest {
            val packageInstance = aScriptPackageInstance("instance-1", "com.example.one")
            val repository = FixedScriptInstancesRepository(emptyList())
            val interactor = StartAllTasksInScriptPackageInstanceInteractor(taskManager, repository)

            interactor.run(StartAllTasksInScriptPackageInstanceInteractor.Params(packageInstance))

            coVerify(exactly = 0) { taskManager.startTasksToConcurrencyLimit(any()) }
            // Sanity: this configuration is considered valid.
            assertEquals(emptyMap<String, Any>(), packageInstance.childConfigurations)
        }
}
