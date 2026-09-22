package com.cereal.client.presentation.proxy

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.files.AddProxiesFromFileToGroupInteractor
import com.cereal.client.application.interactor.proxy.CheckProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.CreateProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteAllProxiesFromGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteFailedProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteProxyInteractor
import com.cereal.client.application.interactor.proxy.GetProxiesInteractor
import com.cereal.client.application.interactor.proxy.GetProxyGroupsInteractor
import com.cereal.client.application.interactor.proxy.UpdateProxyGroupInteractor
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.navigation.Root
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyViewModelTest {
    private lateinit var viewModel: ProxyViewModel
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)

    private val getProxyGroupsInteractor: GetProxyGroupsInteractor = mockk(relaxed = true)
    private val createProxyGroupInteractor: CreateProxyGroupInteractor = mockk(relaxed = true)
    private val updateProxyGroupInteractor: UpdateProxyGroupInteractor = mockk(relaxed = true)
    private val deleteProxyGroupInteractor: DeleteProxyGroupInteractor = mockk(relaxed = true)
    private val getProxiesInteractor: GetProxiesInteractor = mockk(relaxed = true)
    private val deleteProxyInteractor: DeleteProxyInteractor = mockk(relaxed = true)
    private val deleteAllProxiesFromGroupInteractor: DeleteAllProxiesFromGroupInteractor = mockk(relaxed = true)
    private val deleteFailedProxiesInGroupInteractor: DeleteFailedProxiesInGroupInteractor = mockk(relaxed = true)
    private val addProxiesFromFileToGroupInteractor: AddProxiesFromFileToGroupInteractor = mockk(relaxed = true)
    private val checkProxiesInGroupInteractor: CheckProxiesInGroupInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val menuReselectionCoordinator = MenuReselectionCoordinator()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)

        coEvery { getProxyGroupsInteractor(any()) } returns
            flowOf(
                SuspendableResult.Success(emptyList()),
            )
        coEvery { getProxiesInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            ProxyViewModel(
                scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                dispatcherProvider = dispatcherProvider,
                getProxyGroupsInteractor = getProxyGroupsInteractor,
                createProxyGroupInteractor = createProxyGroupInteractor,
                updateProxyGroupInteractor = updateProxyGroupInteractor,
                deleteProxyGroupInteractor = deleteProxyGroupInteractor,
                getProxiesInteractor = getProxiesInteractor,
                deleteProxyInteractor = deleteProxyInteractor,
                deleteAllProxiesFromGroupInteractor = deleteAllProxiesFromGroupInteractor,
                deleteFailedProxiesInGroupInteractor = deleteFailedProxiesInGroupInteractor,
                addProxiesFromFileToGroupInteractor = addProxiesFromFileToGroupInteractor,
                checkProxiesInGroupInteractor = checkProxiesInGroupInteractor,
                errorResolver = errorResolver,
                menuReselectionCoordinator = menuReselectionCoordinator,
            )
    }

    @Test
    fun `onCreateProxyGroup should show adding dialog`() {
        createViewModel()

        viewModel.onCreateProxyGroup()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.AddingProxyGroup)
    }

    @Test
    fun `closeDialog should hide dialog state`() {
        createViewModel()

        viewModel.onCreateProxyGroup()
        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.AddingProxyGroup)

        viewModel.closeDialog()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.Hidden)
    }

    @Test
    fun `onImportFromFile should show import dialog`() {
        createViewModel()

        viewModel.onImportFromFile(stubGroup())

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.ImportFromFile)
    }

    @Test
    fun `onCloseImportFromFileDialog should hide dialog`() {
        createViewModel()

        viewModel.onImportFromFile(stubGroup())
        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.ImportFromFile)

        viewModel.onCloseImportFromFileDialog()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.Hidden)
    }

    @Test
    fun `observeProxyGroups fills the group view state when groups exist`() {
        coEvery { getProxyGroupsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(stubGroup())))
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.groups.value.size)
        assertTrue(viewModel.groupViewState.value is GroupViewState.Filled)
    }

    @Test
    fun `createGroup invokes the interactor and closes the dialog`() {
        coEvery { createProxyGroupInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ProxyGroup, Exception>) -> Unit>()(SuspendableResult.Success(stubGroup()))
        }
        createViewModel()
        viewModel.onCreateProxyGroup()

        viewModel.createGroup("New group")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { createProxyGroupInteractor(any(), any()) }
        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.Hidden)
    }

    @Test
    fun `editGroup invokes the update interactor when a group is selected`() {
        coEvery { updateProxyGroupInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.editGroup("Renamed")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateProxyGroupInteractor(any(), any()) }
    }

    @Test
    fun `editGroup is a no-op without a selected group`() {
        createViewModel()

        viewModel.editGroup("Renamed")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateProxyGroupInteractor(any(), any()) }
    }

    @Test
    fun `deleteGroup invokes the delete interactor and clears selection`() {
        coEvery { deleteProxyGroupInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.deleteGroup()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteProxyGroupInteractor(any(), any()) }
        assertEquals(null, viewModel.selectedGroup.value)
    }

    @Test
    fun `onEditProxyGroup with selection shows the editing dialog`() {
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.onEditProxyGroup()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.EditingProxyGroup)
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
    fun `onDatasetFileSelected adds proxies from file when a group is selected`() {
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.onDatasetFileSelected(File("proxies.txt"))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { addProxiesFromFileToGroupInteractor(any(), any()) }
    }

    @Test
    fun `onDeleteAllProxiesInGroup invokes the interactor when a group is selected`() {
        coEvery { deleteAllProxiesFromGroupInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.onDeleteAllProxiesInGroup()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteAllProxiesFromGroupInteractor(any(), any()) }
    }

    @Test
    fun `onDeleteProxy invokes the delete proxy interactor`() {
        coEvery { deleteProxyInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        createViewModel()
        val proxy = Proxy(id = UUID.randomUUID(), address = "1.2.3.4", port = 8080, username = null, password = null)

        viewModel.onDeleteProxy(DetailListViewItem(id = proxy, attributes = emptyList()))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteProxyInteractor(any(), any()) }
    }

    @Test
    fun `onConfirmDeleteFailingProxies is a no-op when there are no failed proxies`() {
        createViewModel()

        viewModel.onConfirmDeleteFailingProxies()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.Hidden)
        assertEquals(0, viewModel.failedCountInOpenGroup())
    }

    @Test
    fun `onDeleteFailingProxiesConfirmed deletes failed proxies when a group is selected`() {
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.onDeleteFailingProxiesConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteFailedProxiesInGroupInteractor(any(), any()) }
        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.Hidden)
    }

    @Test
    fun `closeDialog hides the dialog`() {
        createViewModel()
        viewModel.onCreateProxyGroup()

        viewModel.closeDialog()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.Hidden)
    }

    @Test
    fun `onEditProxyGroup without argument shows the editing dialog for the selected group`() {
        createViewModel()
        viewModel.onShowDetails(stubGroup())

        viewModel.onEditProxyGroup()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.EditingProxyGroup)
    }

    @Test
    fun `selecting a group with proxies fills the details and exposes failed count`() {
        coEvery { getProxiesInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(failedProxy())))
        createViewModel()
        // Let the (empty) group observer finish first so it doesn't clear our selection.
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onShowDetails(stubGroup())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.failedCountInOpenGroup())
    }

    @Test
    fun `onConfirmDeleteFailingProxies shows the confirm dialog when failed proxies exist`() {
        coEvery { getProxiesInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(failedProxy())))
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onShowDetails(stubGroup())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onConfirmDeleteFailingProxies()

        assertTrue(viewModel.dialogState.value is ProxyViewState.DialogState.ConfirmDeleteFailing)
    }

    @Test
    fun `onTestAllInGroup marks proxies in flight and clears them as results arrive`() {
        val proxy = failedProxy()
        coEvery { getProxiesInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(proxy)))
        every { checkProxiesInGroupInteractor.run(any()) } returns
            flowOf(CheckProxiesInGroupInteractor.Result(proxy.id, ProxyHealth.Unknown))
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onShowDetails(stubGroup())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onTestAllInGroup()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.inFlightProxyIds.value.isEmpty())
        assertEquals(false, viewModel.isCheckingGroup.value)
    }

    @Test
    fun `menu reselection for the proxy manager closes the open details`() {
        createViewModel()
        viewModel.onShowDetails(stubGroup())
        dispatcher.scheduler.advanceUntilIdle()

        menuReselectionCoordinator.notifyReselected(Root.Routing.ProxyManager)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.selectedGroup.value)
    }

    private fun failedProxy() =
        Proxy(
            id = UUID.randomUUID(),
            address = "1.2.3.4",
            port = 8080,
            username = null,
            password = null,
            health = ProxyHealth(ProxyHealthStatus.FAILED, null, null, "err"),
        )

    private fun stubGroup(): ProxyGroup =
        ProxyGroup(
            id = "group-1",
            name = "Group 1",
            numberOfItems = 0,
            items = emptySequence(),
        )
}
