package com.cereal.client.application.interactor.agent

import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.ScriptPackageGroup
import fixtures.FakeRunMode
import fixtures.GoalRunWorld
import fixtures.anAgentPackage
import fixtures.anAgentWithCheckoutVariant
import io.mockk.coVerify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class StartGoalRunInteractorTest {
    private val world = GoalRunWorld().apply { runBlocking { notificationSettings.setDiscordWebhookEnabled(true) } }

    private val repeatRun =
        mapOf(
            "goal" to ConfigValue.StringValue("Tell me when the pliers are back in stock"),
            "startUrl" to ConfigValue.StringValue("https://shop.example/pliers"),
            "runMode" to ConfigValue.EnumValue(FakeRunMode.Repeat),
            "repeatInterval" to ConfigValue.IntValue(600),
            "maxSteps" to ConfigValue.IntValue(40),
        )

    @Test
    fun `start saves the composer's configuration as the instance's main configuration`() =
        runTest {
            world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatRun))
            val instance = world.scriptInstances.getScriptPackageInstances().single()

            assertEquals(repeatRun, instance.mainConfiguration)
        }

    @Test
    fun `a start for a variant creates an instance that runs that variant`() =
        runTest {
            val agent = anAgentWithCheckoutVariant()
            val checkout = mapOf("productUrl" to ConfigValue.StringValue("https://shop.example/pliers"))

            world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(agent, checkout, variantId = "product-checkout"))
            val instance = world.scriptInstances.getScriptPackageInstances().single()

            assertEquals("product-checkout", instance.variantId)
            assertSame(agent.variants.getValue("product-checkout").script, instance.entryScript)
            assertEquals(checkout, instance.mainConfiguration)
        }

    @Test
    fun `one start creates one single-task instance in the Default group and starts it`() =
        runTest {
            world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatRun))
            val instance = world.scriptInstances.getScriptPackageInstances().single()

            val groups = world.tasksRepository.getTaskGroups().first()
            assertEquals(listOf(ScriptPackageGroup.DEFAULT_GROUP_NAME), groups.map { it.name })
            assertEquals(listOf(instance), world.scriptInstances.getScriptPackageInstances())
            assertEquals(groups.single().id, world.scriptInstances.groupOf[instance.id])
            assertEquals(1, instance.numberOfConcurrentTasks)
            coVerify(exactly = 1) { world.taskManager.createTasks(any()) }
            coVerify(exactly = 1) { world.taskManager.startTasksToConcurrencyLimit(any()) }
        }

    @Test
    fun `start returns the run's tasks to land on`() =
        runTest {
            val taskIds = world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatRun))

            val instance = world.scriptInstances.getScriptPackageInstances().single()
            assertEquals(world.tasksRepository.getTasks(instance).map { it.id }, taskIds)
        }

    @Test
    fun `without a notification channel the start is refused until the user accepts the warning`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(false)

            assertFailsWith<NoNotificationChannelsException> {
                world.startGoalRunInteractor.run(StartGoalRunInteractor.Params(anAgentPackage(), repeatRun))
            }
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())

            world.startGoalRunInteractor.run(
                StartGoalRunInteractor.Params(anAgentPackage(), repeatRun, ignoreMissingNotificationChannels = true),
            )
            assertEquals(1, world.scriptInstances.getScriptPackageInstances().size)
        }
}
