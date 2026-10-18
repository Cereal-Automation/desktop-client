package com.cereal.client.presentation.home

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.agent.GetAiBrowserAgentListingInteractor
import com.cereal.client.application.interactor.agent.InstallAiBrowserAgentInteractor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.settings.ai.ConnectLlmProviderInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryCheckoutProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.settings.ai.LlmPreset
import fixtures.FakeRunMode
import fixtures.FakeScriptInstallProvider
import fixtures.GoalRunWorld
import fixtures.agentConfigurationItems
import fixtures.anAgentListing
import fixtures.anAgentPackage
import fixtures.anAgentRequiringNewerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [HomeViewModel] over the real goal-run graph ([GoalRunWorld]) and in-memory repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val world = GoalRunWorld()
    private val scripts = InMemoryScriptRepository()
    private val notificationSettings = InMemoryNotificationSettingsRepository()
    private val installProvider = FakeScriptInstallProvider(scripts)
    private val llmConfigs =
        InMemoryLlmProviderConfigRepository(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "key", "claude-opus-5-5"))
    private val llmProvider = InMemoryLlmProvider()

    private suspend fun TestScope.homeWith(
        agent: ScriptPackage? = anAgentPackage(),
        listing: MarketplaceScript = anAgentListing(),
    ): HomeViewModel {
        scripts.seed(listOfNotNull(agent))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val marketplace = InMemoryMarketplaceProvider(catalog = listOf(listing))
        val getListing = GetAiBrowserAgentListingInteractor(marketplace)
        val vm =
            HomeViewModel(
                scope = CoroutineScope(dispatcher),
                dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher),
                getScriptsInteractor = GetScriptsInteractor(scripts),
                getSdkVersionInteractor = GetSdkVersionInteractor(InMemoryApplicationRepository()),
                getAiBrowserAgentListingInteractor = getListing,
                installAiBrowserAgentInteractor =
                    InstallAiBrowserAgentInteractor(
                        getListing,
                        InstallMarketplaceScriptInteractor(installProvider, marketplace, InMemoryCheckoutProvider()),
                    ),
                hasNotificationChannelsConfiguredInteractor = HasNotificationChannelsConfiguredInteractor(notificationSettings),
                getOrCreateDefaultTaskGroupInteractor = world.getOrCreateDefaultTaskGroupInteractor,
                startGoalRunInteractor = world.startGoalRunInteractor,
                getLlmProviderConfigInteractor = GetLlmProviderConfigInteractor(llmConfigs),
                connectLlmProviderInteractor = ConnectLlmProviderInteractor(llmProvider, llmConfigs),
                errorResolver = ErrorResolver(),
            )
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `with the agent not installed the slot is the install card`() =
        runTest {
            val vm = homeWith(agent = null)

            assertEquals(HomeComposerSlot.NotInstalled, vm.slot.value)
        }

    @Test
    fun `with an agent that needs a newer client the slot is the update card`() =
        runTest {
            val vm = homeWith(agent = anAgentRequiringNewerClient())

            assertEquals(HomeComposerSlot.UpdateRequired, vm.slot.value)
        }

    @Test
    fun `install shows the composer once the agent lands`() =
        runTest {
            val vm = homeWith(agent = null)

            vm.onInstall()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.Composer, vm.slot.value)
            assertEquals(AgentInstallState.Idle, vm.installState.value)
        }

    @Test
    fun `a failed install keeps the card and shows the error`() =
        runTest {
            installProvider.failWith = CerealException("Download failed")
            val vm = homeWith(agent = null)

            vm.onInstall()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.NotInstalled, vm.slot.value)
            assertEquals(AgentInstallState.Failed("Download failed"), vm.installState.value)
        }

    @Test
    fun `a background sync installing the agent shows the composer`() =
        runTest {
            val vm = homeWith(agent = null)

            scripts.seed(listOf(anAgentPackage()))
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.Composer, vm.slot.value)
        }

    @Test
    fun `a missing AI provider loses to a missing or outdated agent`() {
        val sdk = SemVer(1, 0, 0)

        assertEquals(HomeComposerSlot.NotInstalled, homeComposerSlot(null, sdk, hasAiProvider = false))
        assertEquals(HomeComposerSlot.UpdateRequired, homeComposerSlot(anAgentRequiringNewerClient(), sdk, hasAiProvider = false))
        assertEquals(HomeComposerSlot.NoAiProvider, homeComposerSlot(anAgentPackage(), sdk, hasAiProvider = false))
        assertEquals(HomeComposerSlot.Composer, homeComposerSlot(anAgentPackage(), sdk, hasAiProvider = true))
    }

    @Test
    fun `a listing in maintenance mode flags the composer`() =
        runTest {
            val vm = homeWith(listing = anAgentListing(maintenanceMode = true))

            assertEquals(HomeComposerSlot.Composer, vm.slot.value)
            assertTrue(vm.isUnderMaintenance.value)
        }

    private fun HomeViewModel.fillIn() {
        onGoalChange("Tell me when the pliers are back in stock")
        onStartUrlChange("https://shop.example/pliers")
        onRepeatChange(true)
        onCheckEveryMinutesChange("10")
    }

    @Test
    fun `the composer shows on Home with the script's 5 minute default for Check every`() =
        runTest {
            val vm = homeWith()

            assertEquals(HomeComposerSlot.Composer, vm.slot.value)
            assertEquals("5", vm.checkEveryMinutes.value)
        }

    @Test
    fun `start creates one goal run with the composer's values and keeps the text`() =
        runTest {
            notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith()
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            val instance = world.scriptInstances.getScriptPackageInstances().single()
            assertEquals(ConfigValue.StringValue("Tell me when the pliers are back in stock"), instance.mainConfiguration["goal"])
            assertEquals(ConfigValue.EnumValue(FakeRunMode.Repeat), instance.mainConfiguration["runMode"])
            assertEquals(ConfigValue.IntValue(600), instance.mainConfiguration["repeatInterval"])
            assertEquals("Tell me when the pliers are back in stock", vm.goal.value)
            assertNull(vm.dialog.value)
        }

    @Test
    fun `the script's own validator message shows under the goal and nothing starts`() =
        runTest {
            notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith()
            vm.fillIn()
            vm.onGoalChange("")

            vm.onStart()
            advanceUntilIdle()

            assertEquals("Goal is required", vm.fieldErrors.value["goal"])
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `without a notification channel Start warns first and starts once accepted`() =
        runTest {
            val vm = homeWith()
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()
            assertEquals(HomeDialog.NoNotificationChannelsWarning, vm.dialog.value)
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())

            vm.onNoNotificationChannelsWarningAccepted()
            advanceUntilIdle()
            assertEquals(1, world.scriptInstances.getScriptPackageInstances().size)
        }

    @Test
    fun `more options opens the generic form in the Default group prefilled from the composer`() =
        runTest {
            val vm = homeWith()
            vm.fillIn()

            vm.onMoreOptions()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertEquals(ScriptPackageGroup.DEFAULT_GROUP_NAME, dialog.group.name)
            assertEquals(ConfigValue.StringValue("https://shop.example/pliers"), dialog.prefill.mainConfiguration["startUrl"])
            assertEquals(ConfigValue.IntValue(600), dialog.prefill.mainConfiguration["repeatInterval"])
        }

    @Test
    fun `a missing composer key makes Start open the generic form instead`() =
        runTest {
            notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith(anAgentPackage(agentConfigurationItems().filterNot { it.key == "runMode" }))
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertEquals(ConfigValue.StringValue("Tell me when the pliers are back in stock"), dialog.prefill.mainConfiguration["goal"])
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `with no AI provider the setup card shows with Gemini pre-selected`() =
        runTest {
            llmConfigs.clear()
            val vm = homeWith()

            assertEquals(HomeComposerSlot.NoAiProvider, vm.slot.value)
            assertEquals(LlmPreset.GEMINI, vm.aiSetup.preset.value)
        }

    @Test
    fun `a failing Connect saves nothing and keeps the card with the error`() =
        runTest {
            llmConfigs.clear()
            llmProvider.listModelsError = LlmCallException.Authentication()
            val vm = homeWith()
            vm.aiSetup.apiKeyState.text = "bad-key"

            vm.onConnect()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.NoAiProvider, vm.slot.value)
            assertEquals("Bad API key: the provider rejected it.", vm.connectError.value)
            assertNull(llmConfigs.get())
        }

    @Test
    fun `a passing Connect saves the config and shows the composer`() =
        runTest {
            llmConfigs.clear()
            val vm = homeWith()
            vm.aiSetup.apiKeyState.text = "good-key"

            vm.onConnect()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.Composer, vm.slot.value)
            assertEquals("gemini-2.5-flash", llmConfigs.get()?.model)
            assertEquals(llmConfigs.get(), vm.aiProvider.value)
        }
}
