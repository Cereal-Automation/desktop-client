package com.cereal.client.presentation.settings.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator

class EmailSmtpPortState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator(), EmailPortValidator()))

class EmailPortValidator : com.cereal.client.presentation.view.fields.validator.StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value.isNullOrBlank()) {
            "Port is required"
        } else if (value.toIntOrNull() == null || value.toInt() !in 1..MAX_TCP_PORT) {
            "Invalid port number. Must be between 1 and $MAX_TCP_PORT"
        } else {
            null
        }

    private companion object {
        private const val MAX_TCP_PORT = 65535
    }
}
