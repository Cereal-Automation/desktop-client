package com.cereal.client.presentation.tasks

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** The status chips and group filter on the rendered [TasksScreen]. */
@OptIn(ExperimentalTestApi::class)
class TaskFilterBarScreenTest {
    @Test
    fun theFailedChipNarrowsTheListAndAGroupFilterAppearsWithASecondGroup() =
        runScreenTest {
            runBlocking {
                val tasksRepository = KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java)
                val group = tasksRepository.createScriptInstanceGroup(ScriptPackageGroup.createDefault())
                tasksRepository.createScriptInstanceGroup(ScriptPackageGroup("work", "Work"))
                val setup = aScriptPackageInstance("setup-1", "com.example.monitor")
                val main = aScriptInstance(setup)
                KoinJavaComponent.get<ScriptInstanceRepository>(ScriptInstanceRepository::class.java).addScriptPackageInstance(group.id, setup, main)
                val now = Clock.System.now()
                tasksRepository.addAllTasks(
                    listOf("Watching stock", "Sold out").mapIndexed { index, message ->
                        JobTask(
                            id = "task-${index + 1}",
                            scriptInstance = main,
                            configuration = emptyMap(),
                            statusHistory = listOf(if (index == 0) TaskStatus.Running(message, now) else TaskStatus.Error(message, timestamp = now)),
                            createdAt = now + index.milliseconds,
                        )
                    },
                )
            }

            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Watching stock").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("All groups").assertIsDisplayed()
            onNodeWithText("Work").assertIsDisplayed()
            onNodeWithText("Success").assertDoesNotExist()

            onNodeWithText("Failed").performClick()
            waitForIdle()

            onNodeWithText("Watching stock").assertDoesNotExist()
            onNodeWithText("Sold out").assertIsDisplayed()
        }

    @Test
    fun theSortDropdownShowsTheCurrentSortAndSwitchesIt() =
        runScreenTest {
            runBlocking {
                val tasksRepository = KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java)
                val group = tasksRepository.createScriptInstanceGroup(ScriptPackageGroup.createDefault())
                val setup = aScriptPackageInstance("setup-1", "com.example.monitor")
                KoinJavaComponent.get<ScriptInstanceRepository>(ScriptInstanceRepository::class.java).addScriptPackageInstance(group.id, setup, aScriptInstance(setup))
            }

            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Sort: Newest first").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Sort: Newest first").performClick()
            onNodeWithText("Needs attention").performClick()
            waitForIdle()

            onNodeWithText("Sort: Needs attention").assertIsDisplayed()
        }
}
