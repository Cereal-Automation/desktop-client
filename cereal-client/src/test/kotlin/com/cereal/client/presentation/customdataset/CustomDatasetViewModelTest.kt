package com.cereal.client.presentation.customdataset

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.customdataset.DeleteCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.customdataset.DeleteDatasetItemInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetGroupsInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetsInteractor
import com.cereal.client.application.interactor.customdataset.UpdateCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.files.AddCustomDatasetItemsFromFileToGroupInteractor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.view.group.DetailListViewItem
import com.cereal.client.presentation.view.group.GroupViewState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CustomDatasetViewModelTest {
    private lateinit var viewModel: CustomDatasetViewModel
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)

    private val getCustomDatasetGroupsInteractor: GetCustomDatasetGroupsInteractor = mockk(relaxed = true)
    private val getCustomDatasetsInteractor: GetCustomDatasetsInteractor = mockk(relaxed = true)
    private val updateCustomDatasetGroupInteractor: UpdateCustomDatasetGroupInteractor = mockk(relaxed = true)
    private val deleteCustomDatasetGroupInteractor: DeleteCustomDatasetGroupInteractor = mockk(relaxed = true)
    private val deleteDatasetItemInteractor: DeleteDatasetItemInteractor = mockk(relaxed = true)
    private val addCustomDatasetItemsFromFileToGroupInteractor: AddCustomDatasetItemsFromFileToGroupInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)

        coEvery { getCustomDatasetGroupsInteractor(any()) } returns
            flowOf(
                SuspendableResult.Success(emptyList()),
            )
        coEvery { getCustomDatasetsInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
    }

    private fun stubGroup(id: String = "group-1") =
        CustomDatasetGroup(
            id = id,
            name = "Group $id",
            numberOfItems = 0,
            itemDefinitions = emptyList(),
            items = emptySequence(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            CustomDatasetViewModel(
                scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                dispatcherProvider = dispatcherProvider,
                getCustomDatasetGroupsInteractor = getCustomDatasetGroupsInteractor,
                getCustomDatasetsInteractor = getCustomDatasetsInteractor,
                updateCustomDatasetGroupInteractor = updateCustomDatasetGroupInteractor,
                deleteCustomDatasetGroupInteractor = deleteCustomDatasetGroupInteractor,
                deleteDatasetItemInteractor = deleteDatasetItemInteractor,
                addCustomDatasetItemsFromFileToGroupInteractor = addCustomDatasetItemsFromFileToGroupInteractor,
                errorResolver = errorResolver,
                menuReselectionCoordinator = MenuReselectionCoordinator(),
            )
    }

    @Test
    fun `closeDialog should hide dialog state`() {
        createViewModel()

        viewModel.closeDialog()

        assertTrue(viewModel.dialogState.value is CustomDatasetViewState.DialogState.Hidden)
    }

    @Test
    fun `onCloseImportFromFileDialog should hide dialog when no group selected`() {
        createViewModel()

        viewModel.onImportFromFile()

        viewModel.onCloseImportFromFileDialog()

        assertTrue(viewModel.dialogState.value is CustomDatasetViewState.DialogState.Hidden)
    }

    @Test
    fun `observeCustomDatasets fills the group view state when groups exist`() {
        coEvery { getCustomDatasetGroupsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(stubGroup())))
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.groups.value.size)
        assertTrue(viewModel.groupViewState.value is GroupViewState.Filled)
    }

    @Test
    fun `onShowDetails then onCloseDetails toggles the selected group`() {
        createViewModel()

        viewModel.onShowDetails(stubGroup())
        assertEquals(stubGroup(), viewModel.selectedGroup.value)

        viewModel.onCloseDetails()
        assertEquals(null, viewModel.selectedGroup.value)
    }

    @Test
    fun `onEditCustomDatasetGroup with a group shows the editing dialog`() {
        createViewModel()

        viewModel.onEditCustomDatasetGroup(stubGroup())

        assertTrue(viewModel.dialogState.value is CustomDatasetViewState.DialogState.EditingCustomDatasetGroup)
    }

    @Test
    fun `onImportFromFile with a group shows the import dialog`() {
        createViewModel()

        viewModel.onImportFromFile(stubGroup())

        assertTrue(viewModel.dialogState.value is CustomDatasetViewState.DialogState.ImportFromFile)
    }

    @Test
    fun `deleteGroup invokes the delete interactor when a group is selected`() {
        coEvery { deleteCustomDatasetGroupInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.deleteGroup()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteCustomDatasetGroupInteractor(any(), any()) }
        assertEquals(null, viewModel.selectedGroup.value)
    }

    @Test
    fun `updateGroup invokes the update interactor when a group is selected`() {
        coEvery { updateCustomDatasetGroupInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.updateGroup("Renamed")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateCustomDatasetGroupInteractor(any(), any()) }
        assertTrue(viewModel.dialogState.value is CustomDatasetViewState.DialogState.Hidden)
    }

    @Test
    fun `updateGroup is a no-op without a selected group`() {
        createViewModel()

        viewModel.updateGroup("Renamed")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateCustomDatasetGroupInteractor(any(), any()) }
    }

    @Test
    fun `onDatasetFileSelected adds items from file when a group is selected`() {
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.onDatasetFileSelected(java.io.File("data.csv"))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { addCustomDatasetItemsFromFileToGroupInteractor(any(), any()) }
    }

    @Test
    fun `onDeleteCustomDatasetItem invokes the delete item interactor`() {
        coEvery { deleteDatasetItemInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        val item = CustomDatasetItem(id = UUID.randomUUID(), fields = mapOf("a" to ConfigValue.StringValue("b")))

        viewModel.onDeleteCustomDatasetItem(DetailListViewItem(id = item, attributes = emptyList()))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteDatasetItemInteractor(any(), any()) }
    }

    // region Secret

    private fun secretFieldDefinition() =
        ScriptConfigurationItemDefinition(
            name = "API key",
            description = "A per-task credential",
            key = "apiKey",
            position = 0,
            type = ConfigItemType.SecretConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private fun groupHoldingCredentials(vararg credentials: String): CustomDatasetGroup {
        val items =
            credentials.map {
                CustomDatasetItem(
                    id = UUID.randomUUID(),
                    fields = mapOf("apiKey" to ConfigValue.SecretValue(Secret(it))),
                )
            }
        return CustomDatasetGroup(
            id = "group-secrets",
            name = "Credentials",
            numberOfItems = items.size,
            itemDefinitions = listOf(secretFieldDefinition()),
            items = items.asSequence(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
    }

    private fun renderedRowsFor(group: CustomDatasetGroup): List<DetailListViewItem> {
        coEvery { getCustomDatasetGroupsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(group)))
        coEvery { getCustomDatasetsInteractor(any()) } returns flowOf(SuspendableResult.Success(group.items.toList()))
        createViewModel()
        viewModel.onShowDetails(group)
        dispatcher.scheduler.advanceUntilIdle()

        return viewModel.detailsViewState.value.items
    }

    @Test
    fun `the dataset table renders credentials masked`() {
        // The masking comes from the domain Secret's toString and needs no rendering code of its own.
        // Pinned by a test rather than left implicit: a guarantee that holds only by construction is
        // one refactor from silently disappearing, and this failure would be invisible on screen.
        val rows = renderedRowsFor(groupHoldingCredentials("sk-live-alice", "sk-live-bob"))

        assertEquals(2, rows.size)
        assertEquals(listOf(Secret.MASK), rows[0].attributes)
        assertEquals(listOf(Secret.MASK), rows[1].attributes)
    }

    @Test
    fun `no rendered dataset cell contains a credential in clear text`() {
        val rows = renderedRowsFor(groupHoldingCredentials("sk-live-alice", "sk-live-bob"))

        val rendered = rows.flatMap { it.attributes }.joinToString()
        assertFalse(rendered.contains("sk-live"), rendered)
    }

    // endregion
}
