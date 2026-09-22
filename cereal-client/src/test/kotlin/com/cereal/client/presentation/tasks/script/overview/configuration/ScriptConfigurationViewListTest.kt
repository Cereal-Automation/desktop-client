package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.files.ReadCustomDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadListFileInteractor
import com.cereal.client.application.interactor.files.ReadProxyFileInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders the configuration screen with a list on the in-memory harness.
 *
 * The rows live in a bounded, lazily-rendered list nested inside the screen's own vertical scroll —
 * an arrangement Compose rejects outright when the inner list is unbounded, so it is worth rendering
 * for real rather than trusting the layout by inspection. The same goes for the two row layouts: a
 * card per row versus a plain input per row is a claim about what is on screen, not about which
 * branch ran.
 */
@OptIn(ExperimentalTestApi::class)
class ScriptConfigurationViewListTest {
    @Test
    fun rendersRowsAndKeepsTheActionsOutsideTheScrollingRegion() =
        runScreenTest {
            setScreenContent { ScriptConfigurationView(viewModelWithManyRows(fieldCount = 2)) }

            onNodeWithText("Row 1").assertIsDisplayed()
            // A record of more than one field keeps its per-field labels, so a row stays legible.
            onAllNodesWithText("SKU*").onFirst().assertIsDisplayed()
            onNodeWithText("Add").assertIsDisplayed()
            onNodeWithText("Import from CSV").assertIsDisplayed()
        }

    @Test
    fun rendersASingleFieldRecordCompactlyWithoutTheRowNumberOrFieldLabel() =
        runScreenTest {
            setScreenContent { ScriptConfigurationView(viewModelWithManyRows(fieldCount = 1)) }

            onNodeWithText("Row 1").assertDoesNotExist()
            // Nor a per-field label: the item's own title above the column already names it.
            onNodeWithText("SKU*").assertDoesNotExist()
            onNodeWithText("Targets").assertIsDisplayed()
            // Both actions stay; only the card chrome goes.
            onNodeWithText("Add").assertIsDisplayed()
            onNodeWithText("Import from CSV").assertIsDisplayed()
        }

    private fun viewModelWithManyRows(fieldCount: Int): ScriptConfigurationViewModel {
        val getScriptConfigDefinitionInteractor = mockk<GetScriptConfigDefinitionInteractor>(relaxed = true)
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(
                    GetScriptConfigDefinitionInteractor.Result(
                        listOf(ConfigurationItem(listDefinition(fieldCount), null)),
                        emptyMap(),
                    ),
                ),
            )
        }
        val getInstances = mockk<GetScriptPackageInstancesByPackageNameInteractor>(relaxed = true)
        coEvery { getInstances(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<List<ScriptPackageInstance>, Exception>) -> Unit>()(
                SuspendableResult.Success(emptyList()),
            )
        }
        val dispatcherProvider =
            CoroutinesDispatcherProvider(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)

        return ScriptConfigurationViewModel(
            scriptPackage = mockk<ScriptPackage>(relaxed = true),
            initialScriptPackageInstance = null,
            scope = CoroutineScope(Dispatchers.Unconfined),
            errorResolver = ErrorResolver(),
            dispatcherProvider = dispatcherProvider,
            getScriptConfigDefinitionInteractor = getScriptConfigDefinitionInteractor,
            getScriptPackageInstancesByPackageNameInteractor = getInstances,
            readCustomDatasetFileInteractor = mockk<ReadCustomDatasetFileInteractor>(relaxed = true),
            readProxyFileInteractor = mockk<ReadProxyFileInteractor>(relaxed = true),
            readListFileInteractor = mockk<ReadListFileInteractor>(relaxed = true),
        ).also { viewModel ->
            val state =
                viewModel.scriptConfigurationForm.value!!
                    .mainConfigurationFormSection
                    .getFormFieldStates()
                    .filterIsInstance<ListFieldState>()
                    .single()
            // Enough rows that the list has to scroll inside its own window.
            repeat(50) { state.addRow() }
        }
    }

    /** A record of [fieldCount] fields — one to exercise the compact layout, more for the card layout. */
    private fun listDefinition(fieldCount: Int) =
        ScriptConfigurationItemDefinition(
            name = "Targets",
            description = "Products to purchase",
            key = "targets",
            position = 0,
            type =
                ConfigItemType.ListConfigItem(
                    itemType = ScriptPackage::class,
                    items =
                        listOf(
                            ScriptConfigurationItemDefinition(
                                name = "SKU",
                                description = "Product identifier",
                                key = "sku",
                                position = 0,
                                type = ConfigItemType.StringConfigItem,
                                isNullable = false,
                                stateModifier = null,
                                isScriptIdentifier = false,
                            ),
                            ScriptConfigurationItemDefinition(
                                name = "Quantity",
                                description = "How many to buy",
                                key = "qty",
                                position = 1,
                                type = ConfigItemType.IntConfigItem,
                                isNullable = false,
                                stateModifier = null,
                                isScriptIdentifier = false,
                            ),
                        ).take(fieldCount),
                ),
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )
}
