package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Raised when generating proxies from a provider fails (transport error, rejected request, or an empty
 * result). Nothing is written when this is thrown.
 */
class ProxySyncFailedException(
    providerName: String,
    cause: Throwable? = null,
) : CerealException(
        "Couldn't sync proxies from $providerName. Please try again.",
        cause,
    )
