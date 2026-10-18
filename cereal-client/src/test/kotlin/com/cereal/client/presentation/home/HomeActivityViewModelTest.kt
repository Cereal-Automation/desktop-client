package com.cereal.client.presentation.home

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aGoalRun
import fixtures.aScriptPackage
import fixtures.aTask
import fixtures.anAgentPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [HomeActivityViewModel] over in-memory task and script repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeActivityViewModelTest {
    private val tasks = InMemoryTasksRepository()
    private val scripts = InMemoryScriptRepository()

    private fun TestScope.homeActivity(): HomeActivityViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return HomeActivityViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher),
            observeTasksInteractor = ObserveTasksInteractor(tasks),
            getScriptsInteractor = GetScriptsInteractor(scripts),
        )
    }

    @Test
    fun `shows the 3 newest goal runs, newest first, and skips other scripts' tasks`() =
        runTest {
            tasks.addAllTasks(
                listOf(
                    aGoalRun("t1", goal = "First", createdAtSeconds = 1),
                    aGoalRun("t2", goal = "Second", createdAtSeconds = 2),
                    aTask("other", packageName = "com.example.other", running = false),
                    aGoalRun("t3", goal = "Third", createdAtSeconds = 3),
                    aGoalRun("t4", goal = "Fourth", createdAtSeconds = 4),
                ),
            )
            val vm = homeActivity()
            advanceUntilIdle()

            assertEquals(listOf("t4", "t3", "t2"), vm.recentRuns.value.map { it.taskId })
        }

    @Test
    fun `a goal run shows its goal, site, mode, status and latest cost`() =
        runTest {
            tasks.addTask(
                aGoalRun(
                    "t1",
                    goal = "Tell me when the pliers are back in stock",
                    startUrl = "https://shop.example/pliers",
                    repeat = true,
                    statusMessages = listOf("Cost: $0.01 so far", "Starting Chrome", "Not in stock yet. Cost: $0.03"),
                ),
            )
            val vm = homeActivity()
            advanceUntilIdle()

            assertEquals(
                RecentRunUiModel(
                    taskId = "t1",
                    status = "Running",
                    goal = "Tell me when the pliers are back in stock",
                    site = "shop.example",
                    mode = "Repeat",
                    cost = "$0.03",
                ),
                vm.recentRuns.value.single(),
            )
        }

    @Test
    fun `a new goal run appears at the top`() =
        runTest {
            tasks.addTask(aGoalRun("t1", goal = "Old", createdAtSeconds = 1))
            val vm = homeActivity()
            advanceUntilIdle()

            tasks.addTask(aGoalRun("t2", goal = "New", createdAtSeconds = 2))
            advanceUntilIdle()

            assertEquals(listOf("t2", "t1"), vm.recentRuns.value.map { it.taskId })
        }

    @Test
    fun `the ready-made strip lists installed scripts except the agent`() =
        runTest {
            val restock = aScriptPackage("com.example.restock").let { it.copy(manifest = it.manifest.copy(name = "Restock Monitor")) }
            scripts.seed(listOf(anAgentPackage(), restock))
            val vm = homeActivity()
            advanceUntilIdle()

            assertEquals(listOf(ReadyMadeScriptUiModel("com.example.restock", "Restock Monitor")), vm.readyMadeScripts.value)
        }
}
