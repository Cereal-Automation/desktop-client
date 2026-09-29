package com.cereal.client.application.exception

/**
 * The marketplace could not be reached (network failure or a 5xx), as opposed to it rejecting the
 * session (a `401`). Transient: callers retry or fall back rather than treating the session as lost.
 */
class MarketplaceUnreachableException(
    cause: Throwable? = null,
) : CerealException("Can't reach the marketplace.", cause)
