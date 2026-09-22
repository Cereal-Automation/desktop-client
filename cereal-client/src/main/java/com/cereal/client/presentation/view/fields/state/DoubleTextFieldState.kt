package com.cereal.client.presentation.view.fields.state

import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

class DoubleTextFieldState(
    validators: List<StringFieldValidator> = listOf(),
    initialValue: Double? = null,
    onValueChange: ((TextFieldState<Double?>) -> Unit)? = null,
) : TextFieldState<Double?>(validators, initialValue?.toString() ?: "", onValueChange) {
    override fun getValidatedValue(): Double? = if (fieldValue.isEmpty() || fieldValue.isBlank()) null else fieldValue.toDoubleOrNull()
}
