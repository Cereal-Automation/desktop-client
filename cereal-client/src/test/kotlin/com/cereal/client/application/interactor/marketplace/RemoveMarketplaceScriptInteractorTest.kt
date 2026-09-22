@file:OptIn(ExperimentalTime::class)

package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.exception.ScriptHasRunningTasksException
import com.cereal.client.application.script.ScriptManager
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Mock-based by design: the running-task guard needs `tasksRepository.getTasks(instance)` to return
 * running tasks, but `InMemoryTasksRepository.getTasks(ScriptPackageInstance)` returns empty by
 * design; the unsubscribe-failure path needs `unsubscribeFromScript` to throw (the in-memory repo is
 * a no-op); and the assertions verify call ordering across `MarketplaceProvider` and the
 * `ScriptManager` service. Configurable mocks are required for these scenarios.
 */
class RemoveMarketplaceScriptInteractorTest {
    private val marketplaceRepository = mockk<MarketplaceProvider>(relaxed = true)
    private val scriptInstanceRepository = mockk<ScriptInstanceRepository>()
    private val tasksRepository = mockk<TasksRepository>()
    private val scriptManager = mockk<ScriptManager>(relaxed = true)
    private lateinit var interactor: RemoveMarketplaceScriptInteractor

    @BeforeEach
    fun setUp() {
        interactor =
            RemoveMarketplaceScriptInteractor(
                marketplaceRepository = marketplaceRepository,
                scriptInstanceRepository = scriptInstanceRepository,
                tasksRepository = tasksRepository,
                scriptManager = scriptManager,
            )
    }

    @Test
    fun `unsubscribes then deletes when no tasks are running`() =
        runTest {
            val scriptPackage = aScriptPackage("com.example.script")
            val instance = mockk<ScriptPackageInstance>()
            coEvery { scriptInstanceRepository.getScriptPackageInstances("com.example.script") } returns listOf(instance)
            coEvery { tasksRepository.getTasks(instance) } returns listOf(aTask(running = false))

            interactor.run(RemoveMarketplaceScriptInteractor.Params(scriptPackage))

            coVerifyOrder {
                marketplaceRepository.unsubscribeFromScript("com.example.script")
                scriptManager.deleteScript(scriptPackage)
            }
        }

    @Test
    fun `throws ScriptHasRunningTasksException when any task is running and does not touch server or local`() =
        runTest {
            val scriptPackage = aScriptPackage("com.example.script")
            val instance = mockk<ScriptPackageInstance>()
            coEvery { scriptInstanceRepository.getScriptPackageInstances("com.example.script") } returns listOf(instance)
            coEvery { tasksRepository.getTasks(instance) } returns listOf(aTask(running = false), aTask(running = true))

            assertThrows<ScriptHasRunningTasksException> {
                interactor.run(RemoveMarketplaceScriptInteractor.Params(scriptPackage))
            }

            coVerify(exactly = 0) { marketplaceRepository.unsubscribeFromScript(any()) }
            coVerify(exactly = 0) { scriptManager.deleteScript(any()) }
        }

    @Test
    fun `does not call deleteScript when unsubscribe fails`() =
        runTest {
            val scriptPackage = aScriptPackage("com.example.script")
            coEvery { scriptInstanceRepository.getScriptPackageInstances("com.example.script") } returns emptyList()
            coEvery { marketplaceRepository.unsubscribeFromScript("com.example.script") } throws RuntimeException("network down")

            assertThrows<RuntimeException> {
                interactor.run(RemoveMarketplaceScriptInteractor.Params(scriptPackage))
            }

            coVerify(exactly = 0) { scriptManager.deleteScript(any()) }
        }

    @Test
    fun `runs the happy path even when there are no instances at all`() =
        runTest {
            val scriptPackage = aScriptPackage("com.example.script")
            coEvery { scriptInstanceRepository.getScriptPackageInstances("com.example.script") } returns emptyList()

            interactor.run(RemoveMarketplaceScriptInteractor.Params(scriptPackage))

            coVerifyOrder {
                marketplaceRepository.unsubscribeFromScript("com.example.script")
                scriptManager.deleteScript(scriptPackage)
            }
        }

    private fun aScriptPackage(packageName: String) =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest =
                Manifest(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(),
            childScripts = emptyMap(),
        )

    private fun aTask(running: Boolean): JobTask {
        val task = mockk<JobTask>()
        every { task.status } returns
            if (running) {
                TaskStatus.Running(timestamp = Clock.System.now())
            } else {
                TaskStatus.Idle(timestamp = Clock.System.now())
            }
        return task
    }
}
