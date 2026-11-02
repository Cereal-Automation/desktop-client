package com.cereal.client.presentation.view.fields.validator

class RequiredStringFieldValidator(
    private val errorMessage: String = "Required",
) : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value?.isNotEmpty() == true && value.isNotBlank()) {
            null
        } else {
            errorMessage
        }
}
