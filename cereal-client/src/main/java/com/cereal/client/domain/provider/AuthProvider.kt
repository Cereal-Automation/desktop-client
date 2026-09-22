package com.cereal.client.domain.provider

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User

/**
 * Adapter to the marketplace auth/account API: authenticating, registering, password recovery, and
 * fetching the user's entitlements (subscriptions and team scripts). This is a *provider* (an
 * outbound integration), distinct from
 * [com.cereal.client.domain.repository.SessionRepository], which owns the local session/token.
 */
interface AuthProvider {
    suspend fun authenticate(
        email: String,
        password: String,
    ): User

    suspend fun register(
        name: String,
        email: String,
        password: String,
    ): User

    suspend fun authenticateGuest(): User

    /**
     * Signs in with an external [OAuthProvider] (Google, Discord, …). The concrete implementation
     * brokers the OAuth flow through the marketplace backend (system browser + loopback), never
     * talking to the provider directly, and returns the authenticated [User] just like [authenticate].
     */
    suspend fun authenticateWith(provider: OAuthProvider): User

    suspend fun getSubscriptions(ignoreCache: Boolean = false): List<Subscription>

    suspend fun invalidateSubscriptionsCache()

    suspend fun getMyTeamScripts(ignoreCache: Boolean = false): List<ScriptEntitlement>

    suspend fun forgotPassword(email: String)
}
