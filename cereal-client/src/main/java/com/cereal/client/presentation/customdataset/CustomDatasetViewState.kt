package com.cereal.client.presentation.customdataset

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition

sealed class CustomDatasetViewState {
    sealed class DialogState {
        data object Hidden : DialogState()

        class EditingCustomDatasetGroup(
            val initialValue: String,
        ) : DialogState()

        data class ImportFromFile(
            val items: List<ScriptConfigurationItemDefinition>,
        ) : DialogState()
    }
}
