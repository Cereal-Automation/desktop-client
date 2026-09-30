package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.auth.PastedSignIn
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.fixtures.FakeMarketplaceDataSource
import com.cereal.client.fixtures.FakeSubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.SystemBrowserOAuthDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.provider.AuthProviderImpl
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.dsl.module
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.net.URI
import java.net.URLDecoder

/**
 * Pasted sign-in through the real loopback/paste race ([SystemBrowserOAuthDataSource] + [AuthProviderImpl])
 * with a fake marketplace exchange; everything else is the in-memory graph.
 */
class HeadlessPastedSignInTest {
    private val marketplace = FakeMarketplaceDataSource()

    private val signedOutWithRealSso: suspend Koin.() -> Unit = {
        get<SessionRepository>().setSessionUser(null)
        val inMemory = get<AuthProvider>()
        val real = AuthProviderImpl(marketplace, get(), FakeSubscriptionDataSource(), SystemBrowserOAuthDataSource(get(), openBrowser = { false }))
        loadModules(listOf(module { single<AuthProvider> { SsoThrough(inMemory, real) } }), allowOverride = true)
    }

    /**
     * Picks the SSO method [downs] rows below the cursor and returns the sign-in link, read from the
     * OSC 8 hyperlink the TUI writes (the in-memory terminal inserts a newline where a real one soft-wraps).
     */
    private suspend fun HeadlessTestScope.startSso(downs: Int): String {
        awaitText("Sign in to Cereal")
        val before = terminal.buffer.length
        repeat(downs) { press(Keys.Down) }
        press(Keys.Enter)
        awaitText("within about a minute")
        return link.findAll(terminal.buffer.substring(before)).last().groupValues[1]
    }

    private fun queryOf(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private suspend fun HeadlessTestScope.pasteAndSubmit(text: String) {
        type(text)
        press(Keys.Enter)
    }

    @Test
    fun `the sign-in link is one bare unwrapped line and pasting the full redirect URL signs in`() =
        runHeadlessTest(seed = signedOutWithRealSso) {
            val url = startSso(downs = 1)

            assertTrue(url.length > 80, "the link must be longer than the terminal to prove it isn't cut")
            assertTrue(url.startsWith("https://marketplace.cereal-automation.com/auth/google/desktop?state="))
            // The frame keeps the link whole (never truncated) and it lands bare, at column 1, over its reserved rows.
            assertTrue(tui.frame(80, 24).contains(linkLine(url)))
            val screen = awaitScreen { lines -> lines.any { it.startsWith("https://") } }
            val row = screen.indexOfFirst { it.startsWith("https://") }
            assertEquals(url, screen[row] + screen[row + 1])

            val q = queryOf(url)
            pasteAndSubmit("${q.getValue("redirect")}/?code=pasted-code&state=${q.getValue("state")}")

            awaitText("[1 Tasks]")
            assertEquals(listOf("pasted-code"), marketplace.exchangedOAuthCodes)
        }

    @Test
    fun `pasting just the query string signs in with Discord`() =
        runHeadlessTest(seed = signedOutWithRealSso) {
            val url = startSso(downs = 2)
            assertTrue(url.contains("/auth/discord/desktop?"))

            pasteAndSubmit("?code=query-code&state=${queryOf(url).getValue("state")}")

            awaitText("[1 Tasks]")
            assertEquals(listOf("query-code"), marketplace.exchangedOAuthCodes)
        }

    @Test
    fun `a loopback callback that arrives first signs in without a paste`() =
        runHeadlessTest(seed = signedOutWithRealSso) {
            val q = queryOf(startSso(downs = 1))

            withContext(Dispatchers.IO) {
                URI("${q.getValue("redirect")}/?code=forwarded-code&state=${q.getValue("state")}").toURL().readText()
            }

            awaitText("[1 Tasks]")
            assertEquals(listOf("forwarded-code"), marketplace.exchangedOAuthCodes)
        }

    @Test
    fun `a link from another attempt is rejected with its own message`() =
        runHeadlessTest(seed = signedOutWithRealSso) {
            startSso(downs = 1)
            pasteAndSubmit("http://127.0.0.1:1/?code=abc&state=from-another-attempt")

            awaitText("! That address is from another sign-in attempt. Start again.")
            assertTrue(marketplace.exchangedOAuthCodes.isEmpty())
        }

    @Test
    fun `an expired or used code says codes last 60 seconds`() =
        runHeadlessTest(seed = {
            signedOutWithRealSso()
            marketplace.oauthExchangeError = AuthenticationException()
        }) {
            val state = queryOf(startSso(downs = 1)).getValue("state")
            pasteAndSubmit("code=stale&state=$state")

            awaitText("! Sign-in code expired or already used (codes last 60 s). Start again.")
        }

    @Test
    fun `each error value from the backend has its own message`() =
        runHeadlessTest(seed = signedOutWithRealSso) {
            val messages =
                mapOf(
                    "cancelled" to "! Sign-in was cancelled.",
                    "unverified" to "! Your email isn't verified with that provider. Verify it, then try again.",
                    "conflict" to "! That email already has a Cereal account. Sign in the way you used before.",
                )
            messages.entries.forEachIndexed { i, (error, message) ->
                // After a failure the cursor stays on Google, so only the first attempt moves down.
                val state = queryOf(startSso(downs = if (i == 0) 1 else 0)).getValue("state")
                pasteAndSubmit("error=$error&state=$state")
                awaitText(message)
            }
            assertTrue(marketplace.exchangedOAuthCodes.isEmpty())
        }

    private val link = Regex("\u001B]8;;(https://[^\u001B]+)\u001B\\\\")

    /** The in-memory provider for everything except SSO, which goes through the real race. */
    private class SsoThrough(
        delegate: AuthProvider,
        private val sso: AuthProvider,
    ) : AuthProvider by delegate {
        override suspend fun authenticateWith(
            provider: OAuthProvider,
            pastedSignIn: PastedSignIn?,
        ) = sso.authenticateWith(provider, pastedSignIn)
    }
}
