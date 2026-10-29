package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue

private enum class ThreeModes { Once, Repeat, Never }

private enum class FourSizes { Small, Medium, Large, Huge }

/** Enum fields with up to three options render as option cards; more stay a dropdown. */
@OptIn(ExperimentalTestApi::class)
class ScriptConfigurationViewOptionCardsTest {
    @Suppress("UNCHECKED_CAST")
    private fun enumItem(
        enumClass: KClass<out Enum<*>>,
        value: Enum<*>?,
        isNullable: Boolean = false,
    ) = ConfigurationItem(
        ScriptConfigurationItemDefinition(
            name = "Mode",
            description = "",
            key = "mode",
            position = 0,
            type = ConfigItemType.EnumConfigItem(enumClass as KClass<Enum<*>>),
            isNullable = isNullable,
            stateModifier = null,
            isScriptIdentifier = false,
        ),
        value?.let { ConfigValue.EnumValue(it) },
        enumClass.java.enumConstants.map { ConfigValue.EnumValue(it) },
    )

    private fun viewModel(item: ConfigurationItem): ScriptConfigurationViewModel {
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
    fun `an enum with three options renders as option cards`() =
        runScreenTest {
            val vm = viewModel(enumItem(ThreeModes::class, ThreeModes.Once))
            setScreenContent { ScriptConfigurationView(vm) }

            onNodeWithText("Once").assertIsSelected()
            onNodeWithText("Never").performClick()
            onNodeWithText("Never").assertIsSelected()
            onNodeWithText("Once").assertIsNotSelected()
        }

    @Test
    fun `option cards start at the left edge, under the field label`() =
        runScreenTest {
            val vm = viewModel(enumItem(ThreeModes::class, ThreeModes.Once))
            setScreenContent { ScriptConfigurationView(vm) }

            val labelLeft = onNodeWithText("Mode").getUnclippedBoundsInRoot().left
            val cardLeft = onNodeWithText("Once").getUnclippedBoundsInRoot().left
            // Only the group's 3dp inset sits between them; end-aligned cards would be hundreds of dp away.
            assertTrue(cardLeft - labelLeft < 10.dp, "cards start ${cardLeft - labelLeft} right of the label")
        }

    @Test
    fun `an enum with four options stays a dropdown`() =
        runScreenTest {
            val vm = viewModel(enumItem(FourSizes::class, FourSizes.Small))
            setScreenContent { ScriptConfigurationView(vm) }

            // A closed dropdown shows only the selected value.
            onNodeWithText("Small").assertExists()
            onNodeWithText("Huge").assertDoesNotExist()
        }

    @Test
    fun `clicking the selected card clears a nullable enum`() =
        runScreenTest {
            val vm = viewModel(enumItem(ThreeModes::class, null, isNullable = true))
            setScreenContent { ScriptConfigurationView(vm) }

            onNodeWithText("Repeat").assertIsNotSelected()
            onNodeWithText("Repeat").performClick()
            onNodeWithText("Repeat").assertIsSelected()
            onNodeWithText("Repeat").performClick()
            onNodeWithText("Repeat").assertIsNotSelected()
        }

    @Test
    fun `clicking the selected card keeps a required enum selected`() =
        runScreenTest {
            val vm = viewModel(enumItem(ThreeModes::class, ThreeModes.Once))
            setScreenContent { ScriptConfigurationView(vm) }

            onNodeWithText("Once").performClick()
            onNodeWithText("Once").assertIsSelected()
        }
}
