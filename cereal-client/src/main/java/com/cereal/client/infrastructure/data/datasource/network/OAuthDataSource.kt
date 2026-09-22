package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.auth.OAuthProvider

/**
 * Drives the browser side of the brokered SSO sign-in (RFC 8252 native-app pattern): opens the
 * system browser at the marketplace backend's desktop OAuth entry point for a given [OAuthProvider]
 * and waits for the backend to redirect a one-time code back to a local `127.0.0.1` loopback listener.
 *
 * The desktop never talks to the identity provider directly and never sees the provider token — only
 * the marketplace one-time code, which is later exchanged for a session token.
 */
interface OAuthDataSource {
    /**
     * Opens the browser for [provider] and suspends until the backend returns a one-time code over
     * loopback.
     * @return the one-time code to exchange for a session token.
     * @throws com.cereal.client.application.exception.OAuthAuthenticationException on cancel/denial,
     * a `state` mismatch, a browser-open failure, or timeout.
     */
    suspend fun obtainOneTimeCode(provider: OAuthProvider): String
}
