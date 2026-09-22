package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Raised when a proxy sync is attempted but no provider account is connected (no resolved sub-user).
 * The user must connect the provider before syncing.
 */
class ProxyProviderNotConnectedException(
    providerName: String,
) : CerealException(
        "Connect your $providerName account before syncing proxies.",
    )
