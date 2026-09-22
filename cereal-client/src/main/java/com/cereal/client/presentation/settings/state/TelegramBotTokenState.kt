package com.cereal.client.presentation.settings.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

class TelegramBotTokenState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator(), TelegramBotTokenValidator()))

class TelegramBotTokenValidator : StringFieldValidator {
    override fun validate(value: String?): String? =
        if (value.isNullOrBlank()) {
            "Bot token is required"
        } else if (!value.matches(BOT_TOKEN_REGEX)) {
            "Invalid bot token format. Expected format: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
        } else {
            null
        }

    companion object {
        private val BOT_TOKEN_REGEX = Regex("\\d+:\\w+")
    }
}
