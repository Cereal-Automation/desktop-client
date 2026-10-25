package com.cereal.client.application.interactor.task

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.script.UnlicensedException
import fixtures.TasksScreenWorld
import fixtures.dataset
import fixtures.datasetField
import fixtures.idle
import fixtures.proxyGroup
import fixtures.running
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.InvalidParameterException

/** [SetConcurrentTaskCountInteractor] on in-memory repositories and a real task manager (see [TasksScreenWorld]). */
class SetConcurrentTaskCountInteractorTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private val interactor = world.setConcurrentTaskCountInteractor

    @Test
    fun `refuses a count that would remove a running task and changes nothing`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), running(), running(), configuration = proxyGroup())

            assertThrows<CerealException> { interactor.run(SetConcurrentTaskCountInteractor.Params(setup, 1)) }

            assertEquals(3, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(3, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `refuses a count below one`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), configuration = proxyGroup())

            assertThrows<InvalidParameterException> { interactor.run(SetConcurrentTaskCountInteractor.Params(setup, 0)) }
        }

    @Test
    fun `refuses a new count for an unlicensed script, as creation does`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), configuration = proxyGroup(), capacity = null)

            assertThrows<UnlicensedException> { interactor.run(SetConcurrentTaskCountInteractor.Params(setup, 2)) }

            assertEquals(1, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(1, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `caps the count at 25, the most a setup can be created with`() =
        runTest {
            val setup = world.setup("setup-1", null, *Array(25) { idle() }, configuration = proxyGroup())

            assertThrows<CerealException> { interactor.run(SetConcurrentTaskCountInteractor.Params(setup, 26)) }

            assertEquals(25, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(25, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `caps a dataset setup at its number of records`() =
        runTest {
            val setup =
                world.setup("setup-1", null, idle(), idle(), idle(), configuration = dataset(records = 3), configurationItems = listOf(datasetField()))

            assertThrows<CerealException> { interactor.run(SetConcurrentTaskCountInteractor.Params(setup, 4)) }

            assertEquals(3, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
        }

    @Test
    fun `a dataset setup keeps one task per record, the count only sets how many run at once`() =
        runTest {
            val setup =
                world.setup("setup-1", null, idle(), idle(), idle(), configuration = dataset(records = 3), configurationItems = listOf(datasetField()))

            interactor.run(SetConcurrentTaskCountInteractor.Params(setup, 1))

            assertEquals(1, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(3, world.tasksRepository.getTasks(setup).size)
        }
}
