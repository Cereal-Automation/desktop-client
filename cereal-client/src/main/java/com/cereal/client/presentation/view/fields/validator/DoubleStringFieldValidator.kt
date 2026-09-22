package com.cereal.client.presentation.view.fields.validator

class DoubleStringFieldValidator : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value?.toDoubleOrNull() == null) {
            "A value with a decimal separator is required"
        } else {
            null
        }
}
