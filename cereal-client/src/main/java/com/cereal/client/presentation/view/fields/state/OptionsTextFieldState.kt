package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator
import com.cereal.sdk.FieldOption
import com.cereal.sdk.FieldOptions
import kotlinx.coroutines.Job

/**
 * A free-text String field whose suggestions come from the script's [fieldOptions]. Any text is
 * accepted; picking an option just sets the text to its id.
 */
class OptionsTextFieldState(
    val fieldOptions: FieldOptions,
    validators: List<StringFieldValidator> = listOf(),
    initialValue: String = "",
    onValueChange: ((TextFieldState<String?>) -> Unit)? = null,
) : StringTextFieldState(validators, initialValue, onValueChange) {
    /** The last loaded options, null until loaded; kept until a `dependsOn` value changes. */
    var options: List<FieldOption>? by mutableStateOf(null)
    var isLoading: Boolean by mutableStateOf(false)
    var failure: String? by mutableStateOf(null)
    var loadJob: Job? = null
    private var dependsOnValues: ScriptConfigurationValues? = null

    /** Drops the cache and cancels a load in flight when the values this field depends on changed. */
    fun onDependsOnValues(values: ScriptConfigurationValues) {
        if (values == dependsOnValues) return
        dependsOnValues = values
        loadJob?.cancel()
        options = null
        failure = null
        isLoading = false
    }
}
