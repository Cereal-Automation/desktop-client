package com.cereal.client.presentation.tasks.dialog

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.util.InteractorRunner
import kotlinx.coroutines.CoroutineScope

class ViewConfigurationViewModel(
    scope: CoroutineScope,
    private val scriptPackageInstance: ScriptPackageInstance,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    val scriptConfigurationItems = mutableStateOf<List<ConfigurationItem>>(emptyList())

    init {
        interactorRunner.launch(
            getScriptConfigDefinitionInteractor,
            GetScriptConfigDefinitionInteractor.Params(
                scriptPackageInstance.definition,
                scriptPackageInstance,
            ),
        ) {
            scriptConfigurationItems.value = it.mainConfiguration + it.childConfigurations.values.flatten()
        }
    }
}
