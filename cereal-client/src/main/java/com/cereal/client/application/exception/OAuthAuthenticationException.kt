package com.cereal.client.application.exception

/**
 * Thrown when a brokered SSO sign-in flow fails: the user cancelled or denied consent, the loopback
 * `state` did not match (possible tampering), the browser could not be opened, or the backend
 * rejected the one-time code. Applies to every provider (Google, Discord, …).
 */
class OAuthAuthenticationException(
    message: String = "Sign-in did not complete. Please try again.",
    cause: Throwable? = null,
) : CerealException(message, cause)
