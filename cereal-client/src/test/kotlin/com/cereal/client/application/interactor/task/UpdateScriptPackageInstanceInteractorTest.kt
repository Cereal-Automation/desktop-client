package com.cereal.client.application.interactor.task

import com.cereal.client.application.exception.TaskSetupHasRunningTasksException
import com.cereal.client.application.interactor.script.CapacityExceededException
import com.cereal.client.application.interactor.script.UnlicensedException
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.configuration.ConfigValue
import fixtures.TasksScreenWorld
import fixtures.dataset
import fixtures.datasetField
import fixtures.idle
import fixtures.running
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** [UpdateScriptPackageInstanceInteractor] on in-memory repositories and a real task manager (see [TasksScreenWorld]). */
class UpdateScriptPackageInstanceInteractorTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private val interactor = world.updateScriptPackageInstanceInteractor

    @Test
    fun `saving an edit updates the setup in place and keeps its tasks`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val taskBefore = world.tasksRepository.getTask("setup-1-task-1")!!
            val newConfiguration = mapOf("name" to ConfigValue.StringValue("edited"))

            interactor.run(UpdateScriptPackageInstanceInteractor.Params(setup.copy(mainConfiguration = newConfiguration)))

            val stored = world.scriptInstances.getScriptPackageInstance("setup-1")
            assertEquals(newConfiguration, stored.mainConfiguration)
            assertEquals(setup.createdAt, stored.createdAt)
            val task = world.tasksRepository.getTask("setup-1-task-1")!!
            assertEquals(taskBefore.statusHistory, task.statusHistory)
            assertEquals(taskBefore.createdAt, task.createdAt)
            assertEquals(newConfiguration, task.configuration)
            assertEquals(newConfiguration, task.scriptInstance.packageInstance.mainConfiguration)
        }

    @Test
    fun `refuses an edit while a task of the setup runs`() =
        runTest {
            val setup = world.setup("setup-1", null, running())

            assertThrows<TaskSetupHasRunningTasksException> {
                interactor.run(UpdateScriptPackageInstanceInteractor.Params(setup.copy(mainConfiguration = mapOf("name" to ConfigValue.StringValue("edited")))))
            }

            assertEquals(emptyMap<String, ConfigValue>(), world.scriptInstances.getScriptPackageInstance("setup-1").mainConfiguration)
        }

    @Test
    fun `refuses an edit to a dataset over the entitled record capacity, as creation does`() =
        runTest {
            val setup =
                world.setup(
                    "setup-1",
                    null,
                    idle(),
                    configuration = dataset(records = 3),
                    configurationItems = listOf(datasetField()),
                    capacity = ScriptCapacity.Limited(5, "records"),
                )

            assertThrows<CapacityExceededException> {
                interactor.run(UpdateScriptPackageInstanceInteractor.Params(setup.copy(mainConfiguration = dataset(records = 6))))
            }

            val stored =
                world.scriptInstances
                    .getScriptPackageInstance("setup-1")
                    .mainConfiguration.values
                    .single()
            assertEquals(3, (stored as ConfigValue.CustomDatasetGroupValue).raw.numberOfItems)
        }

    @Test
    fun `refuses an edit of an unlicensed script`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), capacity = null)

            assertThrows<UnlicensedException> {
                interactor.run(UpdateScriptPackageInstanceInteractor.Params(setup.copy(mainConfiguration = mapOf("name" to ConfigValue.StringValue("edited")))))
            }
        }
}
