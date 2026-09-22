package com.cereal.client.presentation.view.fields.state

import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

class IntTextFieldState(
    validators: List<StringFieldValidator> = listOf(),
    initialValue: Int? = null,
    onValueChange: ((TextFieldState<Int?>) -> Unit)? = null,
) : TextFieldState<Int?>(validators, initialValue?.toString() ?: "", onValueChange) {
    override fun getValidatedValue(): Int? = if (fieldValue.isEmpty() || fieldValue.isBlank()) null else fieldValue.toIntOrNull()
}
