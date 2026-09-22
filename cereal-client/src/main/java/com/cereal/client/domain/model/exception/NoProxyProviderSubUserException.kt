package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Raised when a proxy provider account has no sub-users, so no proxy credentials can be resolved.
 * The user must create a sub-user in the provider dashboard before connecting.
 */
class NoProxyProviderSubUserException(
    providerName: String,
) : CerealException(
        "Your $providerName account has no sub-users yet. Create one in the provider dashboard, then connect again.",
    )
