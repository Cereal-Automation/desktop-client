package com.cereal.client.presentation.tasks.script.overview

import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationViewModel
import com.cereal.client.presentation.view.group.GroupedListViewModel

sealed class ScriptSelectionState {
    sealed class Overview {
        data object Empty : Overview()

        class Filled(
            val viewModel: GroupedListViewModel<*>,
        ) : Overview()
    }

    sealed class Config {
        data object None : Config()

        class Selected(
            val scriptConfigurationViewModel: ScriptConfigurationViewModel,
        ) : Config()
    }
}
