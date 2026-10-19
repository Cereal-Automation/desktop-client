package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigSection
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.sdk.statemodifier.DefaultStateModifier
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ScriptConfigurationViewSectionTest {
    private val advanced = ConfigSection("adv", "Advanced", collapsed = true, order = 0)

    private fun viewModel(): ScriptConfigurationViewModel {
        val item =
            ConfigurationItem(
                ScriptConfigurationItemDefinition(
                    name = "Retries",
                    description = "",
                    key = "retries",
                    position = Int.MAX_VALUE,
                    type = ConfigItemType.StringConfigItem,
                    isNullable = true,
                    stateModifier = DefaultStateModifier,
                    isScriptIdentifier = false,
                    section = advanced,
                ),
                null,
            )
        // Stubbed: the real one reflects over an SDK configuration class.
        val getDefinition = mockk<GetScriptConfigDefinitionInteractor>()
        coEvery { getDefinition(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(GetScriptConfigDefinitionInteractor.Result(listOf(item), emptyMap())),
            )
        }
        return ScriptConfigurationViewModel(
            scriptPackage = mockk(relaxed = true),
            initialScriptPackageInstance = null,
            scope = CoroutineScope(Dispatchers.Unconfined),
            errorResolver = ErrorResolver(),
            dispatcherProvider = CoroutinesDispatcherProvider(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined),
            getScriptConfigDefinitionInteractor = getDefinition,
            getScriptPackageInstancesByPackageNameInteractor = GetScriptPackageInstancesByPackageNameInteractor(InMemoryScriptInstanceRepository()),
            readCustomDatasetFileInteractor = mockk(relaxed = true),
            readProxyFileInteractor = mockk(relaxed = true),
            readListFileInteractor = mockk(relaxed = true),
        )
    }

    @Test
    fun `the collapsed section's disclosure reads Show when closed and Hide when open`() =
        runScreenTest {
            setScreenContent { ScriptConfigurationView(viewModel()) }

            onNodeWithText("SHOW ADVANCED SETTINGS").performClick()

            onNodeWithText("HIDE ADVANCED SETTINGS").assertIsDisplayed()
            onAllNodesWithText("Retries").onFirst().assertIsDisplayed()
        }
}
