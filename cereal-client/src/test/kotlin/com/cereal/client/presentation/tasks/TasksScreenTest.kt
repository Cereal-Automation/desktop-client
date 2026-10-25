package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.time.ExperimentalTime

/** Renders [TasksScreen] on in-memory repositories. */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class, ExperimentalTime::class)
class TasksScreenTest {
    @Test
    fun rendersTasksPaneWithEmptyState() =
        runScreenTest {
            setScreenContent { TasksScreen() }

            // The list state is resolved asynchronously from flows, so wait for it to settle.
            waitUntil { onAllNodesWithText("No task setups yet").fetchSemanticsNodes().isNotEmpty() }

            onAllNodesWithText("Tasks")[0].assertIsDisplayed()
            onNodeWithText("·  0 setups · 0 tasks").assertIsDisplayed()
            onNodeWithText("New group").assertIsDisplayed()
            onAllNodesWithText("New task setup")[0].assertIsDisplayed()
            onNodeWithText("or describe a goal on Home").assertIsDisplayed()
        }

    @Test
    fun rendersASeededTaskSetupAsASectionWithItsRows() =
        runScreenTest {
            runBlocking {
                val group = KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java).createScriptInstanceGroup(ScriptPackageGroup.createDefault())
                val setup = aScriptPackageInstance("setup-1", "com.example.monitor")
                val main = aScriptInstance(setup)
                KoinJavaComponent.get<ScriptInstanceRepository>(ScriptInstanceRepository::class.java).addScriptPackageInstance(group.id, setup, main)
                val now = Clock.System.now()
                KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java).addAllTasks(
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

            onNodeWithText("·  1 setup · 2 tasks").assertIsDisplayed()
            onNodeWithText("DEFAULT · 1").assertIsDisplayed()
            onNodeWithText("Test").assertIsDisplayed()
            onNodeWithText("1 running").assertIsDisplayed()
            onNodeWithText("1 failed").assertIsDisplayed()
            onNodeWithText("Watching stock").assertIsDisplayed()
            onNodeWithText("Sold out").assertIsDisplayed()
        }
}
