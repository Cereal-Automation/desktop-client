package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

class InvalidScriptPackageGroupException(
    message: String,
) : CerealException(message)
