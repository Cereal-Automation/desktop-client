package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Raised when a proxy provider rejects the supplied API token during connect/replace.
 */
class InvalidProxyProviderTokenException(
    providerName: String,
) : CerealException(
        "That token was rejected by $providerName. Check it was copied in full and hasn't been revoked.",
    )
