package com.cereal.client.presentation.customdataset

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.customdataset.DeleteCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.customdataset.DeleteDatasetItemInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetGroupsInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetsInteractor
import com.cereal.client.application.interactor.customdataset.UpdateCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.files.AddCustomDatasetItemsFromFileToGroupInteractor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
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

// Constructor parameters are injected dependencies (interactors and coordinators).
@Suppress("LongParameterList")
class CustomDatasetViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getCustomDatasetGroupsInteractor: GetCustomDatasetGroupsInteractor,
    private val getCustomDatasetsInteractor: GetCustomDatasetsInteractor,
    private val updateCustomDatasetGroupInteractor: UpdateCustomDatasetGroupInteractor,
    private val deleteCustomDatasetGroupInteractor: DeleteCustomDatasetGroupInteractor,
    private val deleteDatasetItemInteractor: DeleteDatasetItemInteractor,
    private val addCustomDatasetItemsFromFileToGroupInteractor: AddCustomDatasetItemsFromFileToGroupInteractor,
    private val errorResolver: ErrorResolver,
    private val menuReselectionCoordinator: MenuReselectionCoordinator,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    private val customDatasetGroupsListViewModel =
        GroupedListViewModel<CustomDatasetGroup>(onSelectedItemChange = {
            updateDatasetItems(it)
        })

    val groupViewState =
        mutableStateOf<GroupViewState>(GroupViewState.Empty)
    val detailsViewState =
        mutableStateOf<DetailViewState>(DetailViewState.NoSelection(""))
    val dialogState =
        mutableStateOf<CustomDatasetViewState.DialogState>(CustomDatasetViewState.DialogState.Hidden)
    val groups = mutableStateOf<List<CustomDatasetGroup>>(emptyList(), referentialEqualityPolicy())
    val selectedGroup = mutableStateOf<CustomDatasetGroup?>(null, referentialEqualityPolicy())

    val errorAction = errorResolver.errorAction

    private var observerDetailsJob: Job? = null

    init {
        observeCustomDatasets()
        observeMenuReselection()
    }

    private fun observeMenuReselection() {
        scope.launch {
            menuReselectionCoordinator.events.collectLatest { route ->
                if (route is Root.Routing.CustomDatasetsManager) {
                    onCloseDetails()
                }
            }
        }
    }

    fun onEditCustomDatasetGroup() {
        val customDatasetGroup = customDatasetGroupsListViewModel.selectedItem ?: return

        dialogState.value = CustomDatasetViewState.DialogState.EditingCustomDatasetGroup(customDatasetGroup.name)
    }

    fun onEditCustomDatasetGroup(group: CustomDatasetGroup) {
        customDatasetGroupsListViewModel.selectItem(group)
        dialogState.value = CustomDatasetViewState.DialogState.EditingCustomDatasetGroup(group.name)
    }

    fun onShowDetails(group: CustomDatasetGroup) {
        selectedGroup.value = group
        customDatasetGroupsListViewModel.selectItem(group)
    }

    fun onCloseDetails() {
        selectedGroup.value = null
        customDatasetGroupsListViewModel.selectItem(null)
    }

    fun deleteGroup() {
        val customDatasetGroup = customDatasetGroupsListViewModel.selectedItem ?: return

        interactorRunner.launch(
            deleteCustomDatasetGroupInteractor,
            DeleteCustomDatasetGroupInteractor.Params(customDatasetGroup),
        ) {
            customDatasetGroupsListViewModel.selectItem(null)
            selectedGroup.value = null
            closeDialog()
        }
    }

    fun onImportFromFile() {
        val customDatasetGroup = customDatasetGroupsListViewModel.selectedItem ?: return
        dialogState.value = CustomDatasetViewState.DialogState.ImportFromFile(customDatasetGroup.itemDefinitions)
    }

    fun onImportFromFile(group: CustomDatasetGroup) {
        customDatasetGroupsListViewModel.selectItem(group)
        dialogState.value = CustomDatasetViewState.DialogState.ImportFromFile(group.itemDefinitions)
    }

    fun onCloseImportFromFileDialog() {
        dialogState.value = CustomDatasetViewState.DialogState.Hidden
    }

    fun onDatasetFileSelected(file: File) {
        val customDatasetGroup = customDatasetGroupsListViewModel.selectedItem ?: return

        interactorRunner.launch(
            addCustomDatasetItemsFromFileToGroupInteractor,
            AddCustomDatasetItemsFromFileToGroupInteractor.Params(
                file,
                customDatasetGroup,
            ),
        ) {
            dialogState.value = CustomDatasetViewState.DialogState.Hidden
            // Items are updated through the flow.
        }
    }

    fun updateGroup(groupName: String) {
        val customDatasetGroup = customDatasetGroupsListViewModel.selectedItem ?: return

        interactorRunner.launch(
            updateCustomDatasetGroupInteractor,
            UpdateCustomDatasetGroupInteractor.Params(customDatasetGroup, groupName),
        ) {
            closeDialog()
        }
    }

    fun closeDialog() {
        dialogState.value = CustomDatasetViewState.DialogState.Hidden
    }

    fun onDeleteCustomDatasetItem(item: DetailListViewItem) {
        val customDatasetItem = item.id as CustomDatasetItem

        delete(customDatasetItem)
    }

    private fun observeCustomDatasets() {
        scope.launch(dispatcherProvider.io) {
            getCustomDatasetGroupsInteractor(Interactor.None()).collectLatest { result ->
                val mappedResult =
                    result.map {
                        it.toCustomDatasetGroupUiModels()
                    }

                withContext(dispatcherProvider.main) {
                    mappedResult.handleFailureOrElse(errorResolver) { customDatasetGroupsUi ->
                        val newGroups = customDatasetGroupsUi.map { it.id }
                        groups.value = newGroups
                        // If the currently selected group disappeared upstream, clear the
                        // selection so we don't keep a stale detail view open.
                        selectedGroup.value =
                            selectedGroup.value?.let { current ->
                                newGroups.find { it.id == current.id }
                            }
                        if (selectedGroup.value == null) {
                            customDatasetGroupsListViewModel.selectItem(null)
                        }
                        if (newGroups.isEmpty()) {
                            groupViewState.value = GroupViewState.Empty
                        } else {
                            customDatasetGroupsListViewModel.updateItems(customDatasetGroupsUi, true)
                            groupViewState.value = GroupViewState.Filled(customDatasetGroupsListViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun updateDatasetItems(customDatasetGroup: CustomDatasetGroup?) {
        observerDetailsJob?.cancel()

        customDatasetGroup?.let {
            val groupName = customDatasetGroup.name
            detailsViewState.value = DetailViewState.Loading(groupName)

            observerDetailsJob =
                scope.launch(dispatcherProvider.io) {
                    getCustomDatasetsInteractor(GetCustomDatasetsInteractor.Params(customDatasetGroup)).collectLatest { result ->
                        withContext(dispatcherProvider.main) {
                            result.handleFailureOrElse(errorResolver) {
                                detailsViewState.value =
                                    if (it.isEmpty()) {
                                        DetailViewState.Empty(groupName)
                                    } else {
                                        // header/title are vestigial on the shared
                                        // DetailViewState — the new pane renders neither.
                                        DetailViewState.Filled(
                                            DetailListViewHeader(headers = emptyList()),
                                            it.toCustomDatasetUiModels(customDatasetGroup.itemDefinitions),
                                            groupName,
                                        )
                                    }
                            }
                        }
                    }
                }
        } ?: run {
            detailsViewState.value = DetailViewState.NoSelection("Script datasets")
        }
    }

    private fun List<CustomDatasetGroup>.toCustomDatasetGroupUiModels(): List<GroupListItemContent<CustomDatasetGroup>> = map { it.toUiModel() }

    private fun List<CustomDatasetItem>.toCustomDatasetUiModels(itemDefinitions: List<ScriptConfigurationItemDefinition>): List<DetailListViewItem> = map { it.toUiModel(itemDefinitions) }

    private fun CustomDatasetGroup.toUiModel(): GroupListItemContent<CustomDatasetGroup> =
        GroupListItemContent(
            id = this,
            title = this.name,
            badge = this.numberOfItems.toString(),
        )

    private fun CustomDatasetItem.toUiModel(itemDefinitions: List<ScriptConfigurationItemDefinition>): DetailListViewItem =
        DetailListViewItem(
            id = this,
            attributes =
                itemDefinitions.map { definition ->
                    this.fields[definition.key]?.raw?.toString()
                },
        )

    private fun delete(customDatasetItem: CustomDatasetItem) {
        interactorRunner.launch(
            deleteDatasetItemInteractor,
            DeleteDatasetItemInteractor.Params(customDatasetItem),
        ) {
        }
    }
}
