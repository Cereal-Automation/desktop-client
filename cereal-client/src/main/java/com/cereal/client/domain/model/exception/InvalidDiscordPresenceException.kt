package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

class InvalidDiscordPresenceException(
    message: String,
) : CerealException(message)
