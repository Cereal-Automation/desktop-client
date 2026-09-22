package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

@Stable
abstract class TextFieldState<V>(
    validators: List<StringFieldValidator> = listOf(),
    initialValue: String = "",
    private val onValueChange: ((TextFieldState<V>) -> Unit)? = null,
) : FormFieldState<String, V?>(validators) {
    override val fieldValue: String
        get() = text

    var text: String by mutableStateOf(initialValue)

    fun onValueChange(text: String) {
        this.text = text
        onValueChange?.invoke(this)
    }
}
