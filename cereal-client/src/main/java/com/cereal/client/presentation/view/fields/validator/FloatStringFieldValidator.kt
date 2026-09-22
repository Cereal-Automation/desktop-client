package com.cereal.client.presentation.view.fields.validator

class FloatStringFieldValidator : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value?.toFloatOrNull() == null) {
            "A value with a decimal separator is required"
        } else {
            null
        }
}
