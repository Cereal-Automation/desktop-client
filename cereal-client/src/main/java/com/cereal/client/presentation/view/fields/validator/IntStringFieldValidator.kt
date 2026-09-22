package com.cereal.client.presentation.view.fields.validator

class IntStringFieldValidator(
    private val minValue: Int? = null,
    private val maxValue: Int? = null,
) : StringFieldValidator {
    override fun validate(value: String?): String? {
        val intValue = value?.toIntOrNull()
        if (intValue == null) {
            return "A number is required"
        }
        if (minValue != null && intValue < minValue) {
            return "Minimum allowed value is $minValue"
        }
        if (maxValue != null && intValue > maxValue) {
            return "Maximum allowed value is $maxValue"
        }
        return null
    }
}
