package com.cereal.client.presentation.proxy

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
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
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.navigation.Root
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal.client.presentation.view.group.DetailListViewHeader
import com.cereal.client.presentation.view.group.DetailListViewItem
import com.cereal.client.presentation.view.group.DetailViewState
import com.cereal.client.presentation.view.group.GroupListItemContent
import com.cereal.client.presentation.view.group.GroupViewState
import com.cereal.client.presentation.view.group.GroupedListViewModel
import com.github.kittinunf.result.coroutines.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Constructor parameters are injected dependencies (interactors and coordinators).
@Suppress("LongParameterList")
class ProxyViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getProxyGroupsInteractor: GetProxyGroupsInteractor,
    private val createProxyGroupInteractor: CreateProxyGroupInteractor,
    private val updateProxyGroupInteractor: UpdateProxyGroupInteractor,
    private val deleteProxyGroupInteractor: DeleteProxyGroupInteractor,
    private val getProxiesInteractor: GetProxiesInteractor,
    private val deleteProxyInteractor: DeleteProxyInteractor,
    private val deleteAllProxiesFromGroupInteractor: DeleteAllProxiesFromGroupInteractor,
    private val deleteFailedProxiesInGroupInteractor: DeleteFailedProxiesInGroupInteractor,
    private val addProxiesFromFileToGroupInteractor: AddProxiesFromFileToGroupInteractor,
    private val checkProxiesInGroupInteractor: CheckProxiesInGroupInteractor,
    private val errorResolver: ErrorResolver,
    private val menuReselectionCoordinator: MenuReselectionCoordinator,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    private val proxyGroupsListViewModel =
        GroupedListViewModel<ProxyGroup>(onSelectedItemChange = {
            updateProxies(it)
        })

    val groupViewState = mutableStateOf<GroupViewState>(GroupViewState.Empty)
    val detailsViewState = mutableStateOf<DetailViewState>(DetailViewState.NoSelection(""))
    val dialogState = mutableStateOf<ProxyViewState.DialogState>(ProxyViewState.DialogState.Hidden)
    val groups = mutableStateOf<List<ProxyGroup>>(emptyList(), referentialEqualityPolicy())
    val selectedGroup = mutableStateOf<ProxyGroup?>(null, referentialEqualityPolicy())
    val inFlightProxyIds = mutableStateOf<Set<UUID>>(emptySet())
    val isCheckingGroup = mutableStateOf(false)

    val errorAction = errorResolver.errorAction

    private var observerDetailsJob: Job? = null
    private var checkAllJob: Job? = null

    init {
        observeProxyGroups()
        observeMenuReselection()
    }

    private fun observeMenuReselection() {
        scope.launch {
            menuReselectionCoordinator.events.collectLatest { route ->
                if (route is Root.Routing.ProxyManager) {
                    onCloseDetails()
                }
            }
        }
    }

    fun onCreateProxyGroup() {
        dialogState.value = ProxyViewState.DialogState.AddingProxyGroup
    }

    fun onEditProxyGroup() {
        val proxyGroup = proxyGroupsListViewModel.selectedItem ?: return

        dialogState.value = ProxyViewState.DialogState.EditingProxyGroup(proxyGroup.name)
    }

    fun onEditProxyGroup(group: ProxyGroup) {
        proxyGroupsListViewModel.selectItem(group)
        dialogState.value = ProxyViewState.DialogState.EditingProxyGroup(group.name)
    }

    fun onShowDetails(group: ProxyGroup) {
        selectedGroup.value = group
        proxyGroupsListViewModel.selectItem(group)
    }

    fun onCloseDetails() {
        selectedGroup.value = null
        proxyGroupsListViewModel.selectItem(null)
    }

    fun createGroup(groupName: String) {
        interactorRunner.launch(createProxyGroupInteractor, CreateProxyGroupInteractor.Params(groupName)) {
            closeDialog()

            proxyGroupsListViewModel.selectItem(it)
        }
    }

    fun editGroup(groupName: String) {
        val proxyGroupId = proxyGroupsListViewModel.selectedItem?.id ?: return

        interactorRunner.launch(updateProxyGroupInteractor, UpdateProxyGroupInteractor.Params(proxyGroupId, groupName)) {
            closeDialog()
        }
    }

    fun deleteGroup() {
        val proxyGroup = proxyGroupsListViewModel.selectedItem

        interactorRunner.launch(deleteProxyGroupInteractor, DeleteProxyGroupInteractor.Params(proxyGroup)) {
            proxyGroupsListViewModel.selectItem(null)
            selectedGroup.value = null
            closeDialog()
        }
    }

    fun onImportFromFile(group: ProxyGroup) {
        proxyGroupsListViewModel.selectItem(group)
        dialogState.value = ProxyViewState.DialogState.ImportFromFile
    }

    fun onCloseImportFromFileDialog() {
        dialogState.value = ProxyViewState.DialogState.Hidden
    }

    fun onDatasetFileSelected(file: File) {
        val proxyGroup = proxyGroupsListViewModel.selectedItem ?: return

        interactorRunner.launch(
            addProxiesFromFileToGroupInteractor,
            AddProxiesFromFileToGroupInteractor.Params(
                file,
                proxyGroup,
            ),
        ) {
            dialogState.value = ProxyViewState.DialogState.Hidden
            // Proxies are updated through the flow.
        }
    }

    fun onDeleteProxy(proxyUiModel: DetailListViewItem) {
        val proxy = proxyUiModel.id as Proxy

        delete(proxy)
    }

    fun onDeleteAllProxiesInGroup() {
        val proxyGroup = proxyGroupsListViewModel.selectedItem ?: return

        deleteAllFromGroup(proxyGroup)
    }

    /** Snapshot count of currently-failed proxies in the open group (UI-only convenience). */
    fun failedCountInOpenGroup(): Int = currentProxies().count { it.health.status == ProxyHealthStatus.FAILED }

    fun onConfirmDeleteFailingProxies() {
        val failed = failedCountInOpenGroup()
        if (failed == 0) return
        dialogState.value = ProxyViewState.DialogState.ConfirmDeleteFailing(failed)
    }

    fun onDeleteFailingProxiesConfirmed() {
        val proxyGroup = proxyGroupsListViewModel.selectedItem ?: return
        dialogState.value = ProxyViewState.DialogState.Hidden
        interactorRunner.launch(deleteFailedProxiesInGroupInteractor, DeleteFailedProxiesInGroupInteractor.Params(proxyGroup)) {
            // Proxies are updated through the flow.
        }
    }

    private fun currentProxies(): List<Proxy> =
        (detailsViewState.value as? DetailViewState.Filled)
            ?.items
            ?.mapNotNull { it.id as? Proxy }
            .orEmpty()

    fun onTestAllInGroup() {
        val proxyGroup = proxyGroupsListViewModel.selectedItem ?: return
        if (checkAllJob?.isActive == true) return

        isCheckingGroup.value = true
        checkAllJob =
            scope.launch(dispatcherProvider.io) {
                try {
                    checkProxiesInGroupInteractor
                        .run(CheckProxiesInGroupInteractor.Params(proxyGroup))
                        .collect { result ->
                            withContext(dispatcherProvider.main) {
                                inFlightProxyIds.value = inFlightProxyIds.value - result.proxyId
                            }
                        }
                } finally {
                    withContext(dispatcherProvider.main) {
                        inFlightProxyIds.value = emptySet()
                        isCheckingGroup.value = false
                    }
                }
            }

        // Mark every proxy currently in the group as in-flight; results clear them one by one.
        val current =
            (detailsViewState.value as? DetailViewState.Filled)
                ?.items
                ?.mapNotNull { (it.id as? Proxy)?.id }
                ?.toSet()
                .orEmpty()
        inFlightProxyIds.value = current
    }

    private fun observeProxyGroups() {
        groupViewState.value = GroupViewState.Loading

        scope.launch(dispatcherProvider.io) {
            getProxyGroupsInteractor(Interactor.None()).collectLatest { result ->
                val mappedResult =
                    result.map {
                        it.toProxyGroupUiModels()
                    }

                withContext(dispatcherProvider.main) {
                    mappedResult.handleFailureOrElse(errorResolver) { proxyGroupsUi ->
                        val newGroups = proxyGroupsUi.map { it.id }
                        groups.value = newGroups
                        // If the currently selected group disappeared upstream, clear the
                        // selection so we don't keep a stale detail view open.
                        selectedGroup.value =
                            selectedGroup.value?.let { current ->
                                newGroups.find { it.id == current.id }
                            }
                        if (selectedGroup.value == null) {
                            proxyGroupsListViewModel.selectItem(null)
                        }
                        if (newGroups.isEmpty()) {
                            groupViewState.value = GroupViewState.Empty
                        } else {
                            proxyGroupsListViewModel.updateItems(proxyGroupsUi, true)
                            groupViewState.value = GroupViewState.Filled(proxyGroupsListViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun updateProxies(proxyGroup: ProxyGroup?) {
        observerDetailsJob?.cancel()

        proxyGroup?.let {
            val groupName = proxyGroup.name
            detailsViewState.value = DetailViewState.Loading(groupName)

            observerDetailsJob =
                scope.launch(dispatcherProvider.io) {
                    getProxiesInteractor(GetProxiesInteractor.Params(proxyGroup)).collectLatest { result ->
                        withContext(dispatcherProvider.main) {
                            result.handleFailureOrElse(errorResolver) {
                                if (it.isEmpty()) {
                                    detailsViewState.value = DetailViewState.Empty(groupName)
                                } else {
                                    // header/title are kept on the legacy DetailViewState base for
                                    // older detail renderers; the redesigned pane reads neither.
                                    detailsViewState.value =
                                        DetailViewState.Filled(
                                            DetailListViewHeader(headers = emptyList()),
                                            it.toProxyUiModels(),
                                            groupName,
                                        )
                                }
                            }
                        }
                    }
                }
        } ?: run {
            detailsViewState.value = DetailViewState.NoSelection("")
        }
    }

    private fun delete(proxy: Proxy) {
        interactorRunner.launch(deleteProxyInteractor, DeleteProxyInteractor.Params(proxy)) {
        }
    }

    private fun deleteAllFromGroup(proxyGroup: ProxyGroup) {
        interactorRunner.launch(deleteAllProxiesFromGroupInteractor, DeleteAllProxiesFromGroupInteractor.Params(proxyGroup)) {
        }
    }

    /*
     * Miscellaneous
     * */
    fun closeDialog() {
        dialogState.value = ProxyViewState.DialogState.Hidden
    }

    private fun List<ProxyGroup>.toProxyGroupUiModels(): List<GroupListItemContent<ProxyGroup>> = map { it.toUiModel() }

    private fun List<Proxy>.toProxyUiModels(): List<DetailListViewItem> = map { it.toUiModel() }

    private fun ProxyGroup.toUiModel(): GroupListItemContent<ProxyGroup> =
        GroupListItemContent(
            id = this,
            title = this.name,
            badge = this.numberOfItems.toString(),
        )

    private fun Proxy.toUiModel(): DetailListViewItem =
        DetailListViewItem(
            id = this,
            attributes =
                listOf(
                    this.address,
                    this.port.toString(),
                    this.username,
                    this.password,
                ),
        )
}
