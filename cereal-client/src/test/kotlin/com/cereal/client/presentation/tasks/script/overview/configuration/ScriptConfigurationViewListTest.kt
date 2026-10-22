package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
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
import com.cereal.client.presentation.view.fields.SECRET_FIELD_REVEAL_TOGGLE_TAG
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
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
 * A list is one table whose columns come from the record's field definitions, so the same assertions
 * hold for a one-field and a multi-field record. The rows flow inline in the screen's own vertical
 * scroll, with no scrolling region of their own.
 */
@OptIn(ExperimentalTestApi::class)
class ScriptConfigurationViewListTest {
    @Test
    fun rendersAColumnPerRecordFieldWithTheActionsAroundTheTable() =
        runScreenTest {
            setScreenContent { ScriptConfigurationView(viewModelWithManyRows(fieldCount = 2)) }

            onNodeWithText("Targets").assertIsDisplayed()
            // The header is built from the record's fields, required ones marked.
            onNodeWithText("SKU").assertIsDisplayed()
            onNodeWithText("QUANTITY").assertIsDisplayed()
            onAllNodesWithText("*").onFirst().assertIsDisplayed()
            onNodeWithText("Import from CSV").assertIsDisplayed()
            // Fifty-one rows push the add action below the fold; it scrolls with the screen.
            onNodeWithText("Add row").assertExists()
        }

    @Test
    fun rendersASingleFieldRecordAsTheSameTable() =
        runScreenTest {
            setScreenContent { ScriptConfigurationView(viewModelWithManyRows(fieldCount = 1)) }

            onNodeWithText("SKU").assertIsDisplayed()
            onNodeWithText("QUANTITY").assertDoesNotExist()
            onNodeWithText("Add row").assertExists()
        }

    @Test
    fun countsOnlyRowsWithEveryRequiredFieldFilledIn() =
        runScreenTest {
            val viewModel = viewModelWithManyRows(fieldCount = 2, extraRows = 1)
            val row = viewModel.listState().rows.first()
            (row.fieldStates.getValue("sku") as TextFieldState<*>).onValueChange("A1")
            setScreenContent { ScriptConfigurationView(viewModel) }

            // One field of two filled is not a complete row.
            onNodeWithText("0/2").assertIsDisplayed()
            (row.fieldStates.getValue("qty") as TextFieldState<*>).onValueChange("3")
            onNodeWithText("1/2").assertIsDisplayed()
        }

    @Test
    fun listsACellsErrorBelowTheTableNamingItsRowAndColumn() =
        runScreenTest {
            val viewModel = viewModelWithManyRows(fieldCount = 2, extraRows = 0)
            val row = viewModel.listState().rows.first()
            (row.fieldStates.getValue("sku") as TextFieldState<*>).onValueChange("A1")
            viewModel.listState().validate()
            setScreenContent { ScriptConfigurationView(viewModel) }

            onNode(hasText("Row 1 · Quantity:", substring = true)).assertIsDisplayed()
        }

    // region #84 Secret record fields
    @Test
    fun rendersARowSecretMaskedWithARevealToggleOnceItHasAValue() =
        runScreenTest {
            val viewModel = viewModelWithManyRows(fieldCount = 1, withSecret = true, extraRows = 0)
            (
                viewModel
                    .listState()
                    .rows
                    .first()
                    .fieldStates
                    .getValue("password") as TextFieldState<*>
            ).onValueChange("hunter22")
            setScreenContent { ScriptConfigurationView(viewModel) }

            onNodeWithContentDescription("Password")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("••••••••")))
            onAllNodesWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG).onFirst().assertIsDisplayed()
            onAllNodesWithContentDescription("Reveal").onFirst().assertIsDisplayed()
        }
    // endregion

    private fun ScriptConfigurationViewModel.listState(): ListFieldState =
        scriptConfigurationForm.value!!
            .mainConfigurationFormSection
            .getFormFieldStates()
            .filterIsInstance<ListFieldState>()
            .single()

    private fun viewModelWithManyRows(
        fieldCount: Int,
        withSecret: Boolean = false,
        extraRows: Int = 50,
    ): ScriptConfigurationViewModel {
        val getScriptConfigDefinitionInteractor = mockk<GetScriptConfigDefinitionInteractor>(relaxed = true)
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(
                    GetScriptConfigDefinitionInteractor.Result(
                        listOf(ConfigurationItem(listDefinition(fieldCount, withSecret), null)),
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
            // Enough rows, by default, that the list runs past the bottom of the screen.
            repeat(extraRows) { viewModel.listState().addRow() }
        }
    }

    /** A record of [fieldCount] fields — one to exercise the compact layout, more for the card layout. */
    private fun listDefinition(
        fieldCount: Int,
        withSecret: Boolean = false,
    ) = ScriptConfigurationItemDefinition(
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
                    ).take(fieldCount) + listOfNotNull(passwordField().takeIf { withSecret }),
            ),
        isNullable = false,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    private fun passwordField() =
        ScriptConfigurationItemDefinition(
            name = "Password",
            description = "Login",
            key = "password",
            position = 2,
            type = ConfigItemType.SecretConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )
}
