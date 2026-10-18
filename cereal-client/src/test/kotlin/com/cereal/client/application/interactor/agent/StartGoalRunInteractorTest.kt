package com.cereal.client.application.interactor.agent

import com.cereal.client.application.agent.GoalRunInput
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.ScriptPackageGroup
import fixtures.FakeRunMode
import fixtures.GoalRunWorld
import fixtures.agentConfigurationItems
import fixtures.anAgentItem
import fixtures.anAgentPackage
import io.mockk.coVerify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class StartGoalRunInteractorTest {
    private val world = GoalRunWorld().apply { runBlocking { notificationSettings.setDiscordWebhookEnabled(true) } }

    private val repeatInput =
        GoalRunInput(
            goal = "Tell me when the pliers are back in stock",
            startUrl = "https://shop.example/pliers",
            repeat = true,
            repeatIntervalSeconds = 600,
        )

    @Test
    fun `start maps the composer onto the agent's four keys and leaves everything else to the script defaults`() =
        runTest {
            val instance = world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatInput))

            assertEquals(
                mapOf(
                    "goal" to ConfigValue.StringValue("Tell me when the pliers are back in stock"),
                    "startUrl" to ConfigValue.StringValue("https://shop.example/pliers"),
                    "runMode" to ConfigValue.EnumValue(FakeRunMode.Repeat),
                    "repeatInterval" to ConfigValue.IntValue(600),
                ),
                instance.mainConfiguration,
            )
        }

    @Test
    fun `once maps onto the script's own Once constant`() =
        runTest {
            val instance =
                world.startGoalRunInteractor.run(
                    StartGoalRunInteractor.Params(anAgentPackage(), repeatInput.copy(repeat = false)),
                )

            assertEquals(ConfigValue.EnumValue(FakeRunMode.Once), instance.mainConfiguration["runMode"])
        }

    @Test
    fun `one start creates one single-task instance in the Default group and starts it`() =
        runTest {
            val instance = world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatInput))

            val groups = world.tasksRepository.getTaskGroups().first()
            assertEquals(listOf(ScriptPackageGroup.DEFAULT_GROUP_NAME), groups.map { it.name })
            assertEquals(listOf(instance), world.scriptInstances.getScriptPackageInstances())
            assertEquals(groups.single().id, world.scriptInstances.groupOf[instance.id])
            assertEquals(1, instance.numberOfConcurrentTasks)
            coVerify(exactly = 1) { world.taskManager.createTasks(any()) }
            coVerify(exactly = 1) { world.taskManager.startTasksToConcurrencyLimit(any()) }
        }

    @Test
    fun `a missing composer key refuses the start and creates nothing`() =
        runTest {
            val withoutStartUrl = anAgentPackage(agentConfigurationItems().filterNot { it.key == "startUrl" })

            assertFailsWith<GoalComposerUnsupportedException> {
                world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(withoutStartUrl, repeatInput))
            }
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `a retyped composer key refuses the start`() =
        runTest {
            val retyped =
                anAgentPackage(
                    agentConfigurationItems().map {
                        if (it.key == "repeatInterval") anAgentItem("repeatInterval", ConfigItemType.StringConfigItem, 3) else it
                    },
                )

            assertFailsWith<GoalComposerUnsupportedException> {
                world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(retyped, repeatInput))
            }
        }

    @Test
    fun `without a notification channel the start is refused until the user accepts the warning`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(false)

            assertFailsWith<NoNotificationChannelsException> {
                world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatInput))
            }
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())

            world.startGoalRunInteractor.run(
                StartGoalRunInteractor.Params(anAgentPackage(), repeatInput, ignoreMissingNotificationChannels = true),
            )
            assertEquals(1, world.scriptInstances.getScriptPackageInstances().size)
        }
}
