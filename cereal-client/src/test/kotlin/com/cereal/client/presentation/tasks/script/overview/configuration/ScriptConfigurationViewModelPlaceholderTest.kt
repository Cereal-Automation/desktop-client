package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptConfigurationViewModelPlaceholderTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)

    // Stubbed: the real one reflects over an SDK configuration class, while these tests hand-build their items.
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor = mockk()

    private object PlaceholderModifier : StateModifier {
        override fun getVisibility(scriptConfig: ScriptConfig) = Visibility.VisibleOptional

        override fun getError(scriptConfig: ScriptConfig): String? = null

        override fun getPlaceholder(scriptConfig: ScriptConfig): String {
            val apiKey = (scriptConfig.valueForKey("apiKey") as? ScriptConfigValue.StringScriptConfigValue)?.value
            return "Account for ${apiKey ?: "nobody"}"
        }
    }

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
        stateModifier: StateModifier? = null,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "",
        key = key,
        position = Int.MAX_VALUE,
        type = ConfigItemType.StringConfigItem,
        isNullable = true,
        stateModifier = stateModifier,
        isScriptIdentifier = false,
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
            errorResolver = ErrorResolver(),
            dispatcherProvider = dispatcherProvider,
            getScriptConfigDefinitionInteractor = getScriptConfigDefinitionInteractor,
            getScriptPackageInstancesByPackageNameInteractor =
                GetScriptPackageInstancesByPackageNameInteractor(InMemoryScriptInstanceRepository()),
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
