package com.cereal.client.presentation.tasks.dialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.error.ErrorResolver
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [ImportFromFileDialog] on the in-memory harness with a custom dataset type so the
 * "Required Fields:" section is shown. Asserts the action buttons render, that Import is disabled
 * while no file is selected, and that the close button wiring fires [closeDialog].
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ImportFromFileDialogTest {
    private fun aCustomDatasetType(): DatasetType.Custom =
        DatasetType.Custom(
            definitions =
                listOf(
                    ScriptConfigurationItemDefinition(
                        name = "Username",
                        description = "The account username",
                        key = "username",
                        position = 0,
                        type = ConfigItemType.StringConfigItem,
                        isNullable = false,
                        stateModifier = null,
                        isScriptIdentifier = true,
                    ),
                ),
        )

    @Test
    fun rendersDialogSectionsAndActions() =
        runScreenTest {
            setScreenContent {
                ImportFromFileDialog(
                    datasetType = aCustomDatasetType(),
                    closeDialog = {},
                    importFile = {},
                )
            }

            // Title interpolates "CSV" for a custom dataset type.
            onNodeWithText("Import CSV").assertIsDisplayed()
            onNodeWithText("Required Fields:").assertIsDisplayed()
            onNodeWithText("Open file").assertIsDisplayed()
            onNodeWithText("Download template").assertIsDisplayed()
            onNodeWithText("Import").assertIsDisplayed()
        }

    @Test
    fun importButtonDisabledWithoutSelectedFile() =
        runScreenTest {
            setScreenContent {
                ImportFromFileDialog(
                    datasetType = aCustomDatasetType(),
                    closeDialog = {},
                    importFile = {},
                )
            }

            onNodeWithTag("import_button").assertIsNotEnabled()
        }

    @Test
    fun closeButtonInvokesCloseDialog() =
        runScreenTest {
            var closed = false
            setScreenContent {
                ImportFromFileDialog(
                    datasetType = aCustomDatasetType(),
                    closeDialog = { closed = true },
                    importFile = {},
                )
            }

            onNodeWithContentDescription("Close").performClick()

            assertTrue(closed, "Clicking close should invoke closeDialog")
        }

    private enum class Size { SMALL, LARGE }

    private fun anListType(): DatasetType.ConfigList =
        DatasetType.ConfigList(
            definitions =
                listOf(
                    ScriptConfigurationItemDefinition(
                        name = "Size",
                        description = "The size to buy",
                        key = "size",
                        position = 0,
                        type = ConfigItemType.EnumConfigItem(@Suppress("UNCHECKED_CAST") (Size::class as KClass<Enum<*>>)),
                        isNullable = false,
                        stateModifier = null,
                        isScriptIdentifier = false,
                    ),
                    ScriptConfigurationItemDefinition(
                        name = "Notify",
                        description = "Send a notification",
                        key = "notify",
                        position = 1,
                        type = ConfigItemType.BooleanConfigItem,
                        isNullable = true,
                        stateModifier = null,
                        isScriptIdentifier = false,
                    ),
                ),
        )

    private fun aViewModel(datasetType: DatasetType): ImportFromFileViewModel {
        val dispatcherProvider =
            CoroutinesDispatcherProvider(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
        return ImportFromFileViewModel(
            scope = CoroutineScope(Dispatchers.Unconfined),
            datasetType = datasetType,
            dispatcherProvider = dispatcherProvider,
            errorResolver = ErrorResolver(),
            openDatasetFileInteractor = mockk(relaxed = true),
            downloadDatasetFileInteractor = mockk(relaxed = true),
        )
    }

    @Test
    fun describesEveryFieldOfAComplexListRecord() =
        runScreenTest {
            val datasetType = anListType()
            setScreenContent {
                ImportFromFileDialog(
                    datasetType = datasetType,
                    viewModel = aViewModel(datasetType),
                    closeDialog = {},
                    importFile = {},
                )
            }

            onNodeWithText("Fields:").assertIsDisplayed()
            // The type, whether it is required, and — for the closed types — exactly what is accepted.
            onNodeWithText("one of: SMALL, LARGE, required", substring = true).assertExists()
            onNodeWithText("yes/no (true, yes, 1, false, no, 0), optional", substring = true).assertExists()
        }

    @Test
    fun rendersEveryLineOfAMultiLineErrorMessage() =
        runScreenTest {
            val datasetType = anListType()
            val viewModel = aViewModel(datasetType)
            viewModel.fileErrorMessage.value =
                "2 problems were found:\n" +
                "row 1, column 'qty': '12x' is not a whole number.\n" +
                "row 2, column 'size': 'HUGE' is not one of: SMALL, LARGE."

            setScreenContent {
                ImportFromFileDialog(
                    datasetType = datasetType,
                    viewModel = viewModel,
                    closeDialog = {},
                    importFile = {},
                )
            }

            onNodeWithText("row 1, column 'qty'", substring = true).assertExists()
            onNodeWithText("row 2, column 'size'", substring = true).assertExists()
        }
}
