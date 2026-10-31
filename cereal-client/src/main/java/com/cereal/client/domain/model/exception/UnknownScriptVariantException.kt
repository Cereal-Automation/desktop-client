package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/** A task names a variant its installed script doesn't have, or names none in a script that only has variants. */
class UnknownScriptVariantException(
    variantId: String?,
) : CerealException(
        if (variantId == null) {
            "This task doesn't name one of the script's workflows. Create a new task instead."
        } else {
            "This script's variant '$variantId' is no longer part of the installed script. Create a new task instead."
        },
    )
