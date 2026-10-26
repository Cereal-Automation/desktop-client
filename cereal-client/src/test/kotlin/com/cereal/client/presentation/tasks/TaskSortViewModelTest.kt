package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.presentation.error.ErrorAction
import fixtures.TasksScreenWorld
import fixtures.errored
import fixtures.idle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The sort dropdown's order of each group's task setups, on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSortViewModelTest {
    private val preferences = ControlledPreferences()
    private val world = TasksScreenWorld(StandardTestDispatcher(), preferences)
    private val viewModels = mutableListOf<TasksViewModel>()

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModels += it
            settle()
        }

    private fun settle() = world.dispatcher.scheduler.advanceUntilIdle()

    @AfterEach
    fun tearDown() {
        viewModels.forEach { it.dispose() }
    }

    private fun TasksViewModel.list(): TaskViewState.ListState.Filled = assertIs<TaskViewState.ListState.Filled>(listState.value)

    private fun TasksViewModel.setupIds(): List<String> = list().groups.flatMap { it.setups }.map { it.setupId }

    @Test
    fun `a picked sort reorders the setups and is still picked on the next visit`() =
        runTest {
            world.setup("b", null, idle(), name = "Bravo")
            world.setup("a", null, idle(), name = "Alpha")
            val viewModel = start()
            assertEquals(TaskSetupSort.NEWEST_FIRST, viewModel.list().sort)
            assertEquals(listOf("a", "b"), viewModel.setupIds())

            viewModel.onSortSelected(TaskSetupSort.OLDEST_FIRST)
            settle()

            assertEquals(listOf("b", "a"), viewModel.setupIds())
            val nextVisit = start()
            assertEquals(TaskSetupSort.OLDEST_FIRST, nextVisit.list().sort)
            assertEquals(listOf("b", "a"), nextVisit.setupIds())
        }

    @Test
    fun `needs attention moves a setup up as soon as one of its tasks fails`() =
        runTest {
            world.setup("old", null, idle())
            world.setup("new", null, idle())
            val viewModel = start()
            viewModel.onSortSelected(TaskSetupSort.NEEDS_ATTENTION)
            settle()
            assertEquals(listOf("new", "old"), viewModel.setupIds())

            world.tasksRepository.addStatusHistory("old-task-1", errored())
            settle()

            assertEquals(listOf("old", "new"), viewModel.setupIds())
        }

    @Test
    fun `the list stays Loading until the saved sort loads, then shows the setups in it`() =
        runTest {
            world.setup("old", null, idle())
            world.setup("new", null, idle())
            preferences.setTaskSetupSort(TaskSetupSort.OLDEST_FIRST)
            val gate = CompletableDeferred<Unit>()
            preferences.loadGate = gate
            val viewModel = start()

            assertIs<TaskViewState.ListState.Loading>(viewModel.listState.value)

            gate.complete(Unit)
            settle()

            assertEquals(listOf("old", "new"), viewModel.setupIds())
        }

    @Test
    fun `a saved sort that fails to load shows the default order and reports the error`() =
        runTest {
            world.setup("old", null, idle())
            world.setup("new", null, idle())
            preferences.failLoad = true
            val viewModel = start()

            assertEquals(listOf("new", "old"), viewModel.setupIds())
            assertIs<ErrorAction.Message>(world.errorResolver.errorAction.value)
        }

    @Test
    fun `of two quick picks the last one is shown and stored, even when the first saves slower`() =
        runTest {
            world.setup("a", null, idle())
            preferences.saveDelays = mapOf(TaskSetupSort.OLDEST_FIRST to 100.milliseconds)
            val viewModel = start()

            viewModel.onSortSelected(TaskSetupSort.OLDEST_FIRST)
            viewModel.onSortSelected(TaskSetupSort.NAME)
            settle()

            assertEquals(TaskSetupSort.NAME, viewModel.list().sort)
            assertEquals(TaskSetupSort.NAME, preferences.getTaskSetupSort().first())
        }
}

/** In-memory preferences whose saved-sort load and saves a test can hold up or fail. */
private class ControlledPreferences(
    private val delegate: InMemoryApplicationPreferenceRepository = InMemoryApplicationPreferenceRepository(),
) : ApplicationPreferenceRepository by delegate {
    /** While set and incomplete, loading the saved sort waits for it. */
    var loadGate: CompletableDeferred<Unit>? = null
    var failLoad = false
    var saveDelays: Map<TaskSetupSort, Duration> = emptyMap()

    override suspend fun getTaskSetupSort(): Flow<TaskSetupSort> {
        check(!failLoad) { "Could not load the saved sort" }
        loadGate?.await()
        return delegate.getTaskSetupSort()
    }

    override suspend fun setTaskSetupSort(sort: TaskSetupSort) {
        delay(saveDelays[sort] ?: Duration.ZERO)
        delegate.setTaskSetupSort(sort)
    }
}
