package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

class InvalidScriptEntitlementException(
    message: String,
) : CerealException(message)
