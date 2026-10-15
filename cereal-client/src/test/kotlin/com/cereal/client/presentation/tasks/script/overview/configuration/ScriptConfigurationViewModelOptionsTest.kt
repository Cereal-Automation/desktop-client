package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.FieldOptionsTimeoutException
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.view.fields.state.OptionsLoadStatus
import com.cereal.client.presentation.view.fields.state.OptionsTextFieldState
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.sdk.FieldOption
import com.cereal.sdk.FieldOptions
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptConfigurationViewModelOptionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val errorResolver = ErrorResolver()

    // Stubbed: the real one reflects over an SDK configuration class, while these tests hand-build their items.
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor = mockk()
    private val getScriptPackageInstancesByPackageNameInteractor =
        GetScriptPackageInstancesByPackageNameInteractor(InMemoryScriptInstanceRepository())

    /** Controls what the script's `load` does; each call records the config it received. */
    private class TestOptions(
        override val dependsOn: Set<String> = setOf("apiKey"),
    ) : FieldOptions {
        var behaviour: suspend () -> List<FieldOption> = { emptyList() }
        val receivedConfigs = mutableListOf<ScriptConfig>()

        override suspend fun load(config: ScriptConfig): List<FieldOption> {
            receivedConfigs += config
            return behaviour()
        }
    }

    private object PlaceholderModifier : StateModifier {
        override fun getVisibility(scriptConfig: ScriptConfig) = Visibility.VisibleOptional

        override fun getError(scriptConfig: ScriptConfig): String? = null

        override fun getPlaceholder(scriptConfig: ScriptConfig): String {
            val apiKey = (scriptConfig.valueForKey("apiKey") as? ScriptConfigValue.StringScriptConfigValue)?.value
            return "Account for ${apiKey ?: "nobody"}"
        }
    }

    private val options = TestOptions()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun definition(
        key: String,
        fieldOptions: FieldOptions? = null,
        stateModifier: StateModifier? = null,
        defaultValue: ConfigValue? = null,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "",
        key = key,
        position = Int.MAX_VALUE,
        type = ConfigItemType.StringConfigItem,
        isNullable = true,
        stateModifier = stateModifier,
        isScriptIdentifier = false,
        defaultValue = defaultValue,
        fieldOptions = fieldOptions,
    )

    private fun createViewModel(vararg items: ConfigurationItem): ScriptConfigurationViewModel {
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(GetScriptConfigDefinitionInteractor.Result(items.toList(), emptyMap())),
            )
        }
        return ScriptConfigurationViewModel(
            scriptPackage = mockk<ScriptPackage>(relaxed = true),
            initialScriptPackageInstance = null,
            scope = CoroutineScope(dispatcher),
            errorResolver = errorResolver,
            dispatcherProvider = dispatcherProvider,
            getScriptConfigDefinitionInteractor = getScriptConfigDefinitionInteractor,
            getScriptPackageInstancesByPackageNameInteractor = getScriptPackageInstancesByPackageNameInteractor,
            readCustomDatasetFileInteractor = mockk(relaxed = true),
            readProxyFileInteractor = mockk(relaxed = true),
            readListFileInteractor = mockk(relaxed = true),
        ).also { dispatcher.scheduler.advanceUntilIdle() }
    }

    private fun ScriptConfigurationViewModel.state(key: String) =
        scriptConfigurationForm.value!!
            .getAllConfigurationItemWithFieldStates()
            .entries
            .first { it.key.definition.key == key }
            .value

    private fun ScriptConfigurationViewModel.accountState() = state("account") as OptionsTextFieldState

    private fun formWithOptions() =
        createViewModel(
            ConfigurationItem(definition("apiKey"), null),
            ConfigurationItem(definition("other"), null),
            ConfigurationItem(definition("account", fieldOptions = options), null),
        )

    @Test
    fun `opening the dropdown loads the options in the order received`() {
        options.behaviour = { listOf(FieldOption("b", "Bravo"), FieldOption("a")) }
        val viewModel = formWithOptions()

        viewModel.loadOptions(viewModel.accountState())
        assertTrue(viewModel.accountState().isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.accountState().isLoading)
        assertEquals(listOf(FieldOption("b", "Bravo"), FieldOption("a")), viewModel.accountState().options)
    }

    @Test
    fun `load sees only the current unsaved values of its dependsOn fields`() {
        val viewModel = formWithOptions()
        (viewModel.state("apiKey") as StringTextFieldState).onValueChange("key-1")
        (viewModel.state("other") as StringTextFieldState).onValueChange("not-shared")

        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()

        val config = options.receivedConfigs.single()
        assertEquals(ScriptConfigValue.StringScriptConfigValue("key-1"), config.valueForKey("apiKey"))
        assertEquals(ScriptConfigValue.NullScriptConfigValue, config.valueForKey("other"))
    }

    @Test
    fun `load is not gated on empty dependsOn fields`() {
        val viewModel = formWithOptions()

        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, options.receivedConfigs.size)
    }

    @Test
    fun `typing in the combo box or saving never calls load`() {
        val viewModel = formWithOptions()

        viewModel.accountState().onValueChange("typed")
        viewModel.validate()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(options.receivedConfigs.isEmpty())
    }

    @Test
    fun `options are cached until a dependsOn value changes`() {
        options.behaviour = { listOf(FieldOption("a")) }
        val viewModel = formWithOptions()
        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()

        (viewModel.state("other") as StringTextFieldState).onValueChange("unrelated")
        viewModel.accountState().onValueChange("typed")
        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, options.receivedConfigs.size)

        (viewModel.state("apiKey") as StringTextFieldState).onValueChange("key-2")
        assertNull(viewModel.accountState().options)
        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, options.receivedConfigs.size)
    }

    @Test
    fun `refresh reloads even when options are cached`() {
        val viewModel = formWithOptions()
        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadOptions(viewModel.accountState(), refresh = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, options.receivedConfigs.size)
    }

    @Test
    fun `editing a dependsOn field cancels the load in flight`() {
        val pending = CompletableDeferred<List<FieldOption>>()
        var cancelled = false
        options.behaviour = {
            try {
                pending.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
        }
        val viewModel = formWithOptions()
        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.runCurrent()

        (viewModel.state("apiKey") as StringTextFieldState).onValueChange("key-2")
        dispatcher.scheduler.runCurrent()
        pending.complete(listOf(FieldOption("stale")))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(cancelled)
        assertNull(viewModel.accountState().options)
        assertFalse(viewModel.accountState().isLoading)
    }

    @Test
    fun `a load that outlasts the client timeout shows a timeout message and keeps free entry`() {
        options.behaviour = { awaitCancellation() }
        val viewModel = formWithOptions()
        viewModel.accountState().onValueChange("typed")

        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceTimeBy(14.seconds)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.accountState().isLoading)
        dispatcher.scheduler.advanceTimeBy(2.seconds)
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.accountState().isLoading)
        assertIs<FieldOptionsTimeoutException>((viewModel.accountState().loadStatus as OptionsLoadStatus.Failed).error)
        assertEquals("typed", viewModel.accountState().getValidatedValue())
    }

    @Test
    fun `a failing load shows the exception message and free text still saves`() {
        options.behaviour = { throw IllegalStateException("Invalid API key") }
        val viewModel = formWithOptions()

        viewModel.loadOptions(viewModel.accountState())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.accountState().onValueChange("typed-by-hand")

        assertEquals("Invalid API key", (viewModel.accountState().loadStatus as OptionsLoadStatus.Failed).error.message)
        assertTrue(viewModel.validate())
        val values =
            viewModel.scriptConfigurationForm.value!!
                .getAllFormSections()
                .first()
                .getScriptConfigurationValues()
        assertEquals(ConfigValue.StringValue("typed-by-hand"), values["account"])
    }

    @Test
    fun `placeholder comes from the state modifier and follows value changes`() {
        val viewModel =
            createViewModel(
                ConfigurationItem(definition("apiKey"), null),
                ConfigurationItem(definition("account", stateModifier = PlaceholderModifier), null),
            )
        assertEquals("Account for nobody", viewModel.state("account").placeholder.value)

        (viewModel.state("apiKey") as StringTextFieldState).onValueChange("key-1")

        assertEquals("Account for key-1", viewModel.state("account").placeholder.value)
    }

    @Test
    fun `a default still pre-fills the field as a real value`() {
        val viewModel =
            createViewModel(
                ConfigurationItem(
                    definition("account", stateModifier = PlaceholderModifier),
                    ConfigValue.StringValue("default-account"),
                ),
            )

        assertEquals("default-account", (viewModel.state("account") as StringTextFieldState).text)
    }
}
