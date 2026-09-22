package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemorySessionRepository

/**
 * Hermetic [AuthProvider] for UI tests and the sandboxed (`mock`) flavor. Touches no data sources;
 * authentication always succeeds with the seeded user.
 */
class InMemoryAuthProvider(
    private val user: User = InMemorySessionRepository.DEFAULT_USER,
    private val subscriptions: List<Subscription> = emptyList(),
    private val teamScripts: List<ScriptEntitlement> = emptyList(),
) : AuthProvider {
    /** Emails that [forgotPassword] was called with, so tests can assert on real state. */
    val forgotPasswordEmails = mutableListOf<String>()

    override suspend fun authenticate(
        email: String,
        password: String,
    ): User = user

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): User = user

    override suspend fun authenticateGuest(): User = user

    override suspend fun authenticateWith(provider: OAuthProvider): User = user

    override suspend fun getSubscriptions(ignoreCache: Boolean): List<Subscription> = subscriptions

    override suspend fun invalidateSubscriptionsCache() = Unit

    override suspend fun getMyTeamScripts(ignoreCache: Boolean): List<ScriptEntitlement> = teamScripts

    override suspend fun forgotPassword(email: String) {
        forgotPasswordEmails += email
    }
}
