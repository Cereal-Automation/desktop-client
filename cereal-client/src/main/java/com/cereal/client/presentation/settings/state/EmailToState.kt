package com.cereal.client.presentation.settings.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

class EmailToState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator(), EmailValidator()))

class EmailValidator : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value.isNullOrBlank()) {
            "Email address is required"
        } else if (!value.matches(EMAIL_REGEX)) {
            "Invalid email address format"
        } else {
            null
        }

    companion object {
        private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    }
}
