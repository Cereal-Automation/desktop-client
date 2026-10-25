package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

class UnknownScriptVariantException(
    variantId: String,
) : CerealException("This script's variant '$variantId' is no longer part of the installed script. Create a new task instead.")
