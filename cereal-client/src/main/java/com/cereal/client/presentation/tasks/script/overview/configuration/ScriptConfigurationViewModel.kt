package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.files.ReadCustomDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadListFileInteractor
import com.cereal.client.application.interactor.files.ReadProxyFileInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.isGroup
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsForm
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsFormSection
import com.cereal.client.presentation.tasks.script.overview.configuration.model.FileImportConfig
import com.cereal.client.presentation.tasks.script.overview.configuration.model.NotificationOverridesForm
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.sdk.statemodifier.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

// Constructor parameters are injected dependencies and screen arguments.
@Suppress("LongParameterList")
class ScriptConfigurationViewModel(
    private val scriptPackage: ScriptPackage,
    val initialScriptPackageInstance: ScriptPackageInstance?,
    private val scope: CoroutineScope,
    private val errorResolver: ErrorResolver,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor,
    private val getScriptPackageInstancesByPackageNameInteractor: GetScriptPackageInstancesByPackageNameInteractor,
    private val readCustomDatasetFileInteractor: ReadCustomDatasetFileInteractor,
    private val readProxyFileInteractor: ReadProxyFileInteractor,
    private val readListFileInteractor: ReadListFileInteractor,
) {
    val scriptConfigurationForm: MutableState<ConfigurationItemsForm?> = mutableStateOf(null)
    val showConcurrentTasks: MutableState<Boolean> = mutableStateOf(false)
    val notificationOverridesForm = NotificationOverridesForm()
    val showIndicatesRequiredFieldsHint = mutableStateOf(true)
    val errorAction = errorResolver.errorAction
    val fileImportConfig = mutableStateOf<FileImportConfig?>(null)
    val listReplaceConfirmation = mutableStateOf<ListReplaceConfirmation?>(null)
    val scriptInstructions: String? = scriptPackage.manifest.instructions
    val existingInstances: MutableState<List<ScriptPackageInstance>> = mutableStateOf(emptyList())

    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    init {
        load()
        loadExistingInstances()
    }

    private fun loadExistingInstances() {
        val params =
            GetScriptPackageInstancesByPackageNameInteractor.Params(
                packageName = scriptPackage.manifest.packageName,
            )
        interactorRunner.launch(getScriptPackageInstancesByPackageNameInteractor, params) { instances ->
            existingInstances.value = instances
        }
    }

    fun copyFromInstance(scriptPackageInstance: ScriptPackageInstance) {
        scriptConfigurationForm.value?.applyConfigurationFromInstance(scriptPackageInstance)
    }

    private fun load() {
        interactorRunner.launch(
            getScriptConfigDefinitionInteractor,
            GetScriptConfigDefinitionInteractor.Params(
                scriptPackage = scriptPackage,
                scriptPackageInstance = null,
            ),
        ) { result ->
            val allItems =
                result.mainConfiguration +
                    result.childConfigurations.values.flatten()
            showConcurrentTasks.value = allItems.any { it.definition.type.isGroup }
            scriptConfigurationForm.value =
                ConfigurationItemsForm(
                    mainConfiguration = result.mainConfiguration,
                    childConfigurations = result.childConfigurations,
                ) { item, state, formSection ->
                    onImportClicked(item, state, formSection)
                }

            // Set initial state of fields.
            scriptConfigurationForm.value?.applyStateModifiers()

            // Apply values from initial instance if provided (for duplication).
            initialScriptPackageInstance?.let { instance ->
                scriptConfigurationForm.value?.applyConfigurationFromInstance(instance)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun onDatasetFileSelected(file: File) {
        when (val datasetType = fileImportConfig.value?.datasetType) {
            is DatasetType.Proxy -> {
                readProxyFile(
                    fileImportConfig.value?.formFieldState as DropDownFieldState<ProxyGroup>,
                    file,
                )
            }

            is DatasetType.Custom -> {
                readCustomDatasetFile(
                    datasetType.definitions,
                    fileImportConfig.value?.formFieldState as DropDownFieldState<CustomDatasetGroup>,
                    file,
                )
            }

            is DatasetType.ConfigList -> {
                readListFile(
                    datasetType.definitions,
                    fileImportConfig.value?.formFieldState as ListFieldState,
                    file,
                )
            }

            null -> {
                throw RuntimeException("File import config must be set in order to import files.")
            }
        }

        fileImportConfig.value = null
    }

    /** Applies the rows the user was warned about, replacing whatever the field currently holds. */
    fun confirmListReplace() {
        listReplaceConfirmation.value?.let { it.fieldState.replaceRows(it.rows) }
        listReplaceConfirmation.value = null
    }

    fun cancelListReplace() {
        listReplaceConfirmation.value = null
    }

    fun validate(): Boolean = validateConfigurationForm() && validateNotificationOverrides()

    private fun validateNotificationOverrides(): Boolean = notificationOverridesForm.validate()

    private fun onImportClicked(
        item: ConfigurationItem,
        state: FormFieldState<*, *>,
        formSection: ConfigurationItemsFormSection,
    ) {
        fileImportConfig.value =
            FileImportConfig(
                datasetType =
                    when (val type = item.definition.type) {
                        ConfigItemType.ProxyConfigItem, ConfigItemType.ProxyGroupConfigItem -> {
                            DatasetType.Proxy
                        }

                        // The record's fields are shown in full: a field's state modifier is evaluated
                        // against its own row, so the script-level configuration cannot say which of
                        // them a given row will need.
                        is ConfigItemType.ListConfigItem -> {
                            DatasetType.ConfigList(type.items)
                        }

                        is ConfigItemType.GroupedConfigItem -> {
                            val configValues = formSection.getScriptConfigurationValues()
                            // Filter out the invisible fields.
                            val visibleItems =
                                type.items.filter {
                                    it.stateModifier?.getVisibility(RawScriptConfigValues(configValues)) != Visibility.Hidden
                                }
                            DatasetType.Custom(visibleItems)
                        }

                        else -> {
                            throw UnsupportedOperationException("${item.definition.type} isn't mapped to a dataset type.")
                        }
                    },
                configurationItem = item,
                formFieldState = state,
            )
    }

    fun onCloseImportFromFileDialog() {
        fileImportConfig.value = null
    }

    private fun readProxyFile(
        state: DropDownFieldState<ProxyGroup>,
        file: File,
    ) {
        scope.launch(dispatcherProvider.io) {
            val params =
                ReadProxyFileInteractor.Params(
                    file,
                    scriptPackage.manifest,
                )
            readProxyFileInteractor(params) { result ->
                result.handleFailureOrElse(errorResolver) {
                    // Append item to list of options and select it.
                    state.values.value = listOf(it.group) + state.values.value
                    state.onValueChange(it.group)
                }
            }
        }
    }

    private fun readCustomDatasetFile(
        definitions: List<ScriptConfigurationItemDefinition>,
        state: DropDownFieldState<CustomDatasetGroup>,
        file: File,
    ) {
        scope.launch(dispatcherProvider.io) {
            val params =
                ReadCustomDatasetFileInteractor.Params(
                    file,
                    scriptPackage.manifest,
                    definitions,
                )
            readCustomDatasetFileInteractor(params) { result ->
                result.handleFailureOrElse(errorResolver) {
                    // Append item to list of options and select it.
                    state.values.value = listOf(it.group) + state.values.value
                    state.onValueChange(it.group)
                }
            }
        }
    }

    /**
     * Reads the CSV into rows and applies them, asking first when the field already holds rows the
     * user entered — an import replaces the list rather than appending to it, so a mis-click would
     * otherwise silently discard typed work.
     */
    private fun readListFile(
        definitions: List<ScriptConfigurationItemDefinition>,
        state: ListFieldState,
        file: File,
    ) {
        scope.launch(dispatcherProvider.io) {
            val params = ReadListFileInteractor.Params(file, definitions)
            readListFileInteractor(params) { result ->
                result.handleFailureOrElse(errorResolver) {
                    val existingRowCount = state.enteredRowCount()
                    if (existingRowCount > 0) {
                        listReplaceConfirmation.value =
                            ListReplaceConfirmation(
                                fieldState = state,
                                rows = it.rows,
                                existingRowCount = existingRowCount,
                            )
                    } else {
                        state.replaceRows(it.rows)
                    }
                }
            }
        }
    }

    private fun validateConfigurationForm(): Boolean {
        val form = scriptConfigurationForm.value ?: return true
        val fields =
            form
                .getAllFormFieldStates()
                .filter { it != form.numberOfConcurrentTasks || showConcurrentTasks.value }
        var isValid = true

        fields.forEach {
            if (!it.validate()) {
                isValid = false
            }
        }

        return isValid
    }

    /**
     * A finished import waiting on the user's confirmation because applying it would replace rows they
     * already entered.
     */
    data class ListReplaceConfirmation(
        val fieldState: ListFieldState,
        val rows: ListRows,
        val existingRowCount: Int,
    ) {
        val importedRowCount: Int get() = rows.rows.size
    }
}
