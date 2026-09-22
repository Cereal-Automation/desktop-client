package com.cereal.client.domain.model.auth

/**
 * An external identity provider the client can sign in through, brokered by the marketplace backend
 * (system browser + `127.0.0.1` loopback; see
 * [com.cereal.client.infrastructure.data.datasource.network.OAuthDataSource]).
 *
 * [slug] is the backend's URL segment for the provider: the broker opens
 * `<marketplace>/auth/<slug>/desktop` and the token exchange posts to `<marketplace>/auth/<slug>/exchange`.
 * It must match a provider registered on the backend (config/oauth.php on the web side).
 *
 * This is distinct from Discord *Rich Presence* ([com.cereal.client.domain.provider.DiscordProvider]),
 * which is an unrelated runtime integration — [DISCORD] here is sign-in only.
 */
enum class OAuthProvider(
    val slug: String,
) {
    GOOGLE("google"),
    DISCORD("discord"),
}
