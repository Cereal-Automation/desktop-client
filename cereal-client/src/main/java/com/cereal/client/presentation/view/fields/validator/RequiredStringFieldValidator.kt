package com.cereal.client.presentation.view.fields.validator

class RequiredStringFieldValidator(
    private val errorMessage: String = "A value is required",
) : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value?.isNotEmpty() == true && value.isNotBlank()) {
            null
        } else {
            errorMessage
        }
}
