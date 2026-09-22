package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

class InvalidScriptConfigurationException(
    message: String = "Invalid configuration for script provided, please correct the configuration.",
) : CerealException(message)
