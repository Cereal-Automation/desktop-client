package com.cereal.client.presentation.view.fields.state

import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

class FloatTextFieldState(
    validators: List<StringFieldValidator> = listOf(),
    initialValue: Float? = null,
    onValueChange: ((TextFieldState<Float?>) -> Unit)? = null,
) : TextFieldState<Float?>(validators, initialValue?.toString() ?: "", onValueChange) {
    override fun getValidatedValue(): Float? = if (fieldValue.isEmpty() || fieldValue.isBlank()) null else fieldValue.toFloatOrNull()
}
