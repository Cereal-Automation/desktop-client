package com.cereal.client.presentation.home

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.agent.GetAiBrowserAgentListingInteractor
import com.cereal.client.application.interactor.agent.InstallAiBrowserAgentInteractor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
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
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryCheckoutProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.settings.ai.LlmPreset
import com.cereal.client.presentation.view.UiText
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
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
import fixtures.anAgentWithCheckoutVariant
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
        hasAiProvider: Boolean? = true,
    ): HomeViewModel {
        scripts.seed(listOfNotNull(agent))
        val dispatchers = dispatcherProvider()
        val vm =
            HomeViewModel(
                scope = CoroutineScope(dispatchers.main),
                dispatcherProvider = dispatchers,
                getScriptsInteractor = GetScriptsInteractor(scripts),
                getSdkVersionInteractor = GetSdkVersionInteractor(InMemoryApplicationRepository()),
                getScriptConfigDefinitionInteractor = GetScriptConfigDefinitionInteractor(InMemoryProxyRepository(), InMemoryCustomDatasetRepository()),
                prefillGoalRunFormInteractor = world.prefillGoalRunFormInteractor,
                startGoalRunInteractor = world.startGoalRunInteractor,
                startAllTasksInScriptPackageInstanceInteractor = world.startAllTasksInScriptPackageInstanceInteractor,
                errorResolver = ErrorResolver(),
            )
        hasAiProvider?.let(vm::onAiProviderChanged)
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
    fun `the composer doesn't show before the AI provider is known`() =
        runTest {
            val vm = homeWith(hasAiProvider = null)
            assertEquals(HomeComposerSlot.Loading, vm.slot.value)

            vm.onAiProviderChanged(false)

            assertEquals(HomeComposerSlot.NoAiProvider, vm.slot.value)
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

    private fun HomeViewModel.shownKeys() = composerFields().map { (item, _) -> item.definition.key }

    private fun HomeViewModel.field(key: String) =
        composerForm.value!!
            .configurationItemToFieldState.entries
            .first { it.key.definition.key == key }
            .value

    private fun HomeViewModel.type(
        key: String,
        text: String,
    ) = (field(key) as TextFieldState<*>).onValueChange(text)

    @Suppress("UNCHECKED_CAST")
    private fun HomeViewModel.choose(
        key: String,
        value: Any,
    ) = (field(key) as DropDownFieldState<Any>).onValueChange(value)

    private fun HomeViewModel.fillIn() {
        type("goal", "Tell me when the pliers are back in stock")
        type("startUrl", "https://shop.example/pliers")
        choose("runMode", FakeRunMode.Repeat)
        type("repeatInterval", "600")
    }

    @Test
    fun `in Once Home shows only the fields the script requires, not the ones with defaults`() =
        runTest {
            val vm = homeWith()

            assertEquals(HomeComposerSlot.Composer, vm.slot.value)
            assertEquals(listOf("goal", "startUrl", "runMode"), vm.shownKeys())
        }

    @Test
    fun `switching to Repeat also shows Check every and Vary by with the script's defaults`() =
        runTest {
            val vm = homeWith()

            vm.choose("runMode", FakeRunMode.Repeat)

            assertEquals(listOf("goal", "startUrl", "runMode", "repeatInterval", "repeatJitter"), vm.shownKeys())
            assertEquals("300", (vm.field("repeatInterval") as TextFieldState<*>).text)
        }

    @Test
    fun `a non-null field without a default shows even without a state modifier`() =
        runTest {
            val agent = anAgentPackage(agentConfigurationItems() + anAgentItem("apiKey", ConfigItemType.SecretConfigItem, 10))

            assertEquals(listOf("goal", "startUrl", "runMode", "apiKey"), homeWith(agent).shownKeys())
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
            assertEquals("Tell me when the pliers are back in stock", (vm.field("goal") as TextFieldState<*>).text)
            assertNull(vm.dialog.value)
        }

    @Test
    fun `with an agent that has variants, start creates a goal run of its first variant`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith(anAgentWithCheckoutVariant())
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            val instance = world.scriptInstances.getScriptPackageInstances().single()
            assertEquals("browser-agent", instance.variantId)
            assertEquals(ConfigValue.StringValue("Tell me when the pliers are back in stock"), instance.mainConfiguration["goal"])
        }

    private fun HomeViewModel.pick(name: String) = selectEntry(entryOptions.value.first { it.name == name })

    @Test
    fun `an agent with variants offers only its variants and picks the first`() =
        runTest {
            val vm = homeWith(anAgentWithCheckoutVariant())

            assertEquals(listOf("Custom goal", "Product Checkout"), vm.entryOptions.value.map { it.name })
            assertEquals("Custom goal", vm.selectedEntry.value?.name)
        }

    @Test
    fun `picking a variant shows that variant's fields`() =
        runTest {
            val vm = homeWith(anAgentWithCheckoutVariant())

            vm.pick("Product Checkout")
            advanceUntilIdle()

            assertEquals(listOf("productUrl"), vm.shownKeys())
        }

    @Test
    fun `switching back to the first variant keeps what was typed there`() =
        runTest {
            val vm = homeWith(anAgentWithCheckoutVariant())
            vm.type("goal", "Watch the pliers")

            vm.pick("Product Checkout")
            advanceUntilIdle()
            vm.pick("Custom goal")
            advanceUntilIdle()

            assertEquals("Watch the pliers", (vm.field("goal") as TextFieldState<*>).text)
        }

    @Test
    fun `start on a picked variant creates a goal run of that variant`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith(anAgentWithCheckoutVariant())
            vm.pick("Product Checkout")
            advanceUntilIdle()
            vm.type("productUrl", "https://shop.example/pliers")

            vm.onStart()
            advanceUntilIdle()

            val instance = world.scriptInstances.getScriptPackageInstances().single()
            assertEquals("product-checkout", instance.variantId)
            assertEquals(ConfigValue.StringValue("https://shop.example/pliers"), instance.mainConfiguration["productUrl"])
        }

    @Test
    fun `more options on a picked variant opens the form on that variant`() =
        runTest {
            val vm = homeWith(anAgentWithCheckoutVariant())
            vm.pick("Product Checkout")
            advanceUntilIdle()

            vm.onMoreOptions()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertEquals("product-checkout", dialog.prefill.instance.variantId)
        }

    @Test
    fun `an empty required field shows its error and nothing starts`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val vm = homeWith()
            vm.fillIn()
            vm.type("goal", "")

            vm.onStart()
            advanceUntilIdle()

            assertEquals("A value is required", vm.field("goal").error)
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `the script's own error for a filled-in field shows inline and nothing starts`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val httpOnly =
                object : StateModifier {
                    override fun getVisibility(scriptConfig: ScriptConfig) = Visibility.VisibleRequired

                    override fun getError(scriptConfig: ScriptConfig) = "Start URL must be a web address"
                }
            val agent = anAgentPackage(agentConfigurationItems().map { if (it.key == "startUrl") it.copy(stateModifier = httpOnly) else it })
            val vm = homeWith(agent)
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            assertEquals("Start URL must be a web address", vm.field("startUrl").error)
            assertNull(vm.dialog.value)
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
    fun `a required field Home can't render makes Start open the generic form`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val agent = anAgentPackage(agentConfigurationItems() + anAgentItem("proxies", ConfigItemType.ProxyGroupConfigItem, 10))
            val vm = homeWith(agent)
            vm.fillIn()

            assertEquals(listOf("goal", "startUrl", "runMode", "repeatInterval", "repeatJitter"), vm.shownKeys())
            vm.onStart()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertTrue(dialog.startOnCreate)
            assertEquals(ConfigValue.StringValue("Tell me when the pliers are back in stock"), dialog.prefill.instance.mainConfiguration["goal"])
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `an invalid field Home doesn't show makes Start open the generic form, and launching it runs the goal`() =
        runTest {
            world.notificationSettings.setDiscordWebhookEnabled(true)
            val mustBePositive =
                object : StateModifier {
                    override fun getVisibility(scriptConfig: ScriptConfig) = Visibility.VisibleOptional

                    override fun getError(scriptConfig: ScriptConfig) = "Max steps must be positive"
                }
            val agent =
                anAgentPackage(
                    agentConfigurationItems().map {
                        if (it.key == "maxSteps") it.copy(defaultValue = ConfigValue.IntValue(0), stateModifier = mustBePositive) else it
                    },
                )
            val vm = homeWith(agent)
            vm.fillIn()

            vm.onStart()
            advanceUntilIdle()

            val dialog = assertInstanceOf(HomeDialog.MoreOptions::class.java, vm.dialog.value)
            assertTrue(dialog.startOnCreate)
            assertTrue(vm.composerFields().all { (_, state) -> state.error == null })
            assertTrue(world.scriptInstances.getScriptPackageInstances().isEmpty())

            // The user fixes Max steps in the generic form, which creates the instance (idle tasks) and reports it back.
            val created =
                world.startScriptInteractor.run(
                    StartScriptInteractor.Params(
                        groupId = dialog.prefill.group.id,
                        scriptPackage = anAgentPackage(),
                        mainScriptConfiguration = dialog.prefill.instance.mainConfiguration + ("maxSteps" to ConfigValue.IntValue(40)),
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

            assertEquals("gemini-3.8-flash", llmConfigs.get()?.model)
            assertEquals(HomeAiProviderState.Configured(AiProviderUiModel(LlmPreset.GEMINI, "gemini-3.8-flash")), vm.state.value)
        }
}
