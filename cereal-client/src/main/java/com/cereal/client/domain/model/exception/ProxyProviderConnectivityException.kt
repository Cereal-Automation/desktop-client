package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Raised when the client cannot reach a proxy provider (timeout or connectivity failure).
 */
class ProxyProviderConnectivityException(
    providerName: String,
    cause: Throwable? = null,
) : CerealException(
        "Couldn't reach $providerName. Check your internet connection and try again.",
        cause,
    )
