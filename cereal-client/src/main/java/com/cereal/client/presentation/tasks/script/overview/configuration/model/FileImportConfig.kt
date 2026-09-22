package com.cereal.client.presentation.tasks.script.overview.configuration.model

import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.presentation.view.fields.state.FormFieldState

/**
 * The in-flight file import: which configuration item asked for it, and which form field the result
 * is applied to.
 *
 * [formFieldState] is the general form-field type rather than the dropdown it used to be, because a
 * list imports into a
 * [com.cereal.client.presentation.view.fields.state.ListFieldState], not a dropdown.
 */
data class FileImportConfig(
    val datasetType: DatasetType,
    val configurationItem: ConfigurationItem,
    val formFieldState: FormFieldState<*, *>,
)
