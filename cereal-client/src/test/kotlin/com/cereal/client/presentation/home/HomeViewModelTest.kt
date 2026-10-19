package com.cereal.client.presentation.home

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.agent.GetAiBrowserAgentListingInteractor
import com.cereal.client.application.interactor.agent.InstallAiBrowserAgentInteractor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.settings.ai.ConnectLlmProviderInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryCheckoutProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.settings.ai.LlmPreset
import com.cereal.client.presentation.view.UiText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_connection_bad_key
import fixtures.FakeRunMode
import fixtures.FakeScriptInstallProvider
import fixtures.GoalRunWorld
import fixtures.agentConfigurationItems
import fixtures.anAgentItem
import fixtures.anAgentListing
import fixtures.anAgentPackage
import fixtures.anAgentRequiringNewerClient
import io.mockk.coVerify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Home's ViewModels over the real goal-run graph ([GoalRunWorld]) and in-memory repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val world = GoalRunWorld()
    private val scripts = InMemoryScriptRepository()
    private val installProvider = FakeScriptInstallProvider(scripts)
    private val llmConfigs =
        InMemoryLlmProviderConfigRepository(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "key", "claude-opus-5-5"))
    private val llmProvider = InMemoryLlmProvider()

    private fun TestScope.dispatcherProvider(): CoroutinesDispatcherProvider {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    }

    /** The composer VM, told whether an AI provider is configured as the screen does. */
    private suspend fun TestScope.homeWith(
        agent: ScriptPackage? = anAgentPackage(),
        hasAiProvider: Boolean = true,
    ): HomeViewModel {
        scripts.seed(listOfNotNull(agent))
        val dispatchers = dispatcherProvider()
        val vm =
            HomeViewModel(
                scope = CoroutineScope(dispatchers.main),
                dispatcherProvider = dispatchers,
                getScriptsInteractor = GetScriptsInteractor(scripts),
                getSdkVersionInteractor = GetSdkVersionInteractor(InMemoryApplicationRepository()),
                prefillGoalRunFormInteractor = world.prefillGoalRunFormInteractor,
                startGoalRunInteractor = world.startGoalRunInteractor,
                startAllTasksInScriptPackageInstanceInteractor = world.startAllTasksInScriptPackageInstanceInteractor,
                errorResolver = ErrorResolver(),
            )
        vm.onAiProviderChanged(hasAiProvider)
        advanceUntilIdle()
        return vm
    }

    private suspend fun TestScope.installWith(listing: MarketplaceScript = anAgentListing()): AgentInstallViewModel {
        val dispatchers = dispatcherProvider()
        val marketplace = InMemoryMarketplaceProvider(catalog = listOf(listing))
        val getListing = GetAiBrowserAgentListingInteractor(marketplace)
        val vm =
            AgentInstallViewModel(
                scope = CoroutineScope(dispatchers.main),
                dispatcherProvider = dispatchers,
                getAiBrowserAgentListingInteractor = getListing,
                installAiBrowserAgentInteractor =
                    InstallAiBrowserAgentInteractor(
                        getListing,
                        InstallMarketplaceScriptInteractor(installProvider, marketplace, InMemoryCheckoutProvider()),
                    ),
            )
        advanceUntilIdle()
        return vm
    }

    private suspend fun TestScope.aiProvider(): HomeAiProviderViewModel {
        val dispatchers = dispatcherProvider()
        val vm =
            HomeAiProviderViewModel(
                scope = CoroutineScope(dispatchers.main),
                dispatcherProvider = dispatchers,
                getLlmProviderConfigInteractor = GetLlmProviderConfigInteractor(llmConfigs),
                connectLlmProviderInteractor = ConnectLlmProviderInteractor(llmProvider, SaveLlmProviderConfigInteractor(llmConfigs)),
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
            val home = homeWith(agent = null)
            val install = installWith()

            install.onInstall()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.Composer, home.slot.value)
            assertEquals(AgentInstallState.Idle, install.installState.value)
        }

    @Test
    fun `a failed install keeps the card and shows the error`() =
        runTest {
            installProvider.failWith = CerealException("Download failed")
            val home = homeWith(agent = null)
            val install = installWith()

            install.onInstall()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.NotInstalled, home.slot.value)
            assertEquals(AgentInstallState.Failed(UiText.Raw("Download failed")), install.installState.value)
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
            val install = installWith(listing = anAgentListing(maintenanceMode = true))

            assertTrue(install.isUnderMaintenance.value)
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
            world.notificationSettings.setDiscordWebhookEnabled(true)
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
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith()
            vm.fillIn()
            vm.onGoalChange("")

            vm.onStart()
            advanceUntilIdle()

            assertEquals(UiText.Raw("Goal is required"), vm.fieldErrors.value[ComposerField.GOAL])
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
    fun `with no AI provider Start does nothing, even if reached`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith(hasAiProvider = false)
            vm.fillIn()

            vm.onStart()
            vm.onMoreOptions()
            advanceUntilIdle()

            assertEquals(HomeComposerSlot.NoAiProvider, vm.slot.value)
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
            assertNull(vm.dialog.value)
        }

    @Test
    fun `more options opens the generic form in the Default group prefilled from the composer`() =
        runTest {
            val vm = homeWith()
            vm.fillIn()

            vm.onMoreOptions()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertEquals(ScriptPackageGroup.DEFAULT_GROUP_NAME, dialog.prefill.group.name)
            assertEquals(ConfigValue.StringValue("https://shop.example/pliers"), dialog.prefill.instance.mainConfiguration["startUrl"])
            assertEquals(ConfigValue.IntValue(600), dialog.prefill.instance.mainConfiguration["repeatInterval"])
            assertFalse(dialog.startOnCreate)
        }

    @Test
    fun `a missing composer key makes Start open the generic form, and launching it runs the goal`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val agent = anAgentPackage(agentConfigurationItems().filterNot { it.key == "runMode" })
            val vm = homeWith(agent)
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertEquals(ConfigValue.StringValue("Tell me when the pliers are back in stock"), dialog.prefill.instance.mainConfiguration["goal"])
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())

            // The generic form launches: it creates the instance (idle tasks) and reports it back.
            val created =
                world.startScriptInteractor.run(
                    StartScriptInteractor.Params(
                        groupId = dialog.prefill.group.id,
                        scriptPackage = agent,
                        mainScriptConfiguration = dialog.prefill.instance.mainConfiguration,
                        childConfigurations = emptyMap(),
                        numberOfConcurrentTasks = 1,
                        ignoreConcurrencyConflicts = true,
                    ),
                )
            vm.onMoreOptionsInstanceCreated(created)
            advanceUntilIdle()

            coVerify(exactly = 1) { world.taskManager.startTasksToConcurrencyLimit(any()) }
        }

    @Test
    fun `an invalid item the composer doesn't show makes Start open the generic form instead of failing`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val agent = anAgentPackage(agentConfigurationItems() + anAgentItem("apiKey", ConfigItemType.StringConfigItem, 10))
            val vm = homeWith(agent)
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertTrue(dialog.startOnCreate)
            assertTrue(vm.fieldErrors.value.isEmpty())
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `with no AI provider the setup card shows with Gemini pre-selected`() =
        runTest {
            llmConfigs.clear()
            val vm = aiProvider()

            assertEquals(HomeAiProviderState.None, vm.state.value)
            assertEquals(LlmPreset.GEMINI, vm.setup.preset.value)
        }

    @Test
    fun `a failing Connect saves nothing and keeps the card with the error`() =
        runTest {
            llmConfigs.clear()
            llmProvider.listModelsError = LlmCallException.Authentication()
            val vm = aiProvider()
            vm.setup.apiKeyState.text = "bad-key"

            vm.onConnect()
            advanceUntilIdle()

            assertEquals(HomeAiProviderState.None, vm.state.value)
            assertEquals(UiText.Resource(Res.string.ai_connection_bad_key), vm.connectError.value)
            assertNull(llmConfigs.get())
        }

    @Test
    fun `a passing Connect saves the config and reports the provider`() =
        runTest {
            llmConfigs.clear()
            val vm = aiProvider()
            vm.setup.apiKeyState.text = "good-key"

            vm.onConnect()
            advanceUntilIdle()

            assertEquals("gemini-2.5-flash", llmConfigs.get()?.model)
            assertEquals(HomeAiProviderState.Configured(AiProviderUiModel(LlmPreset.GEMINI, "gemini-2.5-flash")), vm.state.value)
        }
}
