package com.cereal.client.presentation.settings.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

class TelegramChatIdState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator(), TelegramChatIdValidator()))

class TelegramChatIdValidator : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value.isNullOrBlank()) {
            "Chat ID is required"
        } else if (!value.matches(CHAT_ID_REGEX)) {
            "Invalid chat ID format. Expected numeric ID (e.g., 123456789 or -1001234567890)"
        } else {
            null
        }

    companion object {
        private val CHAT_ID_REGEX = Regex("-?\\d+")
    }
}
