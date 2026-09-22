package com.cereal.client.presentation.view.fields.state

import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

open class StringTextFieldState(
    validators: List<StringFieldValidator> = listOf(),
    initialValue: String = "",
    onValueChange: ((TextFieldState<String?>) -> Unit)? = null,
) : TextFieldState<String?>(validators, initialValue, onValueChange) {
    override fun getValidatedValue(): String? = if (fieldValue.isEmpty() || fieldValue.isBlank()) null else fieldValue
}
