package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.exception.OAuthAuthenticationException
import com.cereal.client.domain.model.auth.OAuthProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.net.URLDecoder
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the loopback + state logic without a real browser: the injected `openBrowser` lambda
 * receives the authorize URL, extracts the loopback redirect + state, and calls back into the
 * listener exactly as the marketplace backend would.
 */
class SystemBrowserOAuthDataSourceTest {
    private val config =
        mockk<ApplicationConfig> {
            every { marketplaceBaseUrl } returns "https://marketplace.example.com/"
        }

    private fun queryOf(url: String): Map<String, String> =
        (URI(url).rawQuery ?: "")
            .split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) {
                    null
                } else {
                    URLDecoder.decode(pair.substring(0, idx), Charsets.UTF_8) to
                        URLDecoder.decode(pair.substring(idx + 1), Charsets.UTF_8)
                }
            }.toMap()

    /** Simulate the backend redirecting the browser to the loopback listener. */
    private fun deliver(
        redirect: String,
        query: String,
    ) {
        URI("$redirect?$query")
            .toURL()
            .openStream()
            .bufferedReader()
            .use { it.readText() }
    }

    @Test
    fun `returns the one-time code delivered to the loopback listener`() =
        runBlocking {
            val dataSource =
                SystemBrowserOAuthDataSource(config, openBrowser = { url ->
                    val q = queryOf(url)
                    deliver(q.getValue("redirect"), "code=one-time-code&state=${q.getValue("state")}")
                    true
                })

            val code = dataSource.obtainOneTimeCode(OAuthProvider.GOOGLE)

            assertEquals("one-time-code", code)
        }

    @Test
    fun `opens the browser at the backend desktop entry with a loopback redirect`() =
        runBlocking {
            var openedUrl: String? = null
            val dataSource =
                SystemBrowserOAuthDataSource(config, openBrowser = { url ->
                    openedUrl = url
                    val q = queryOf(url)
                    deliver(q.getValue("redirect"), "code=c&state=${q.getValue("state")}")
                    true
                })

            dataSource.obtainOneTimeCode(OAuthProvider.GOOGLE)

            val opened = requireNotNull(openedUrl)
            val q = queryOf(opened)
            assertTrue(opened.startsWith("https://marketplace.example.com/auth/google/desktop?"))
            assertTrue(q.getValue("redirect").startsWith("http://127.0.0.1:"))
            assertTrue(q.getValue("state").isNotEmpty())
        }

    @Test
    fun `the provider slug selects the backend desktop entry path`() =
        runBlocking {
            var openedUrl: String? = null
            val dataSource =
                SystemBrowserOAuthDataSource(config, openBrowser = { url ->
                    openedUrl = url
                    val q = queryOf(url)
                    deliver(q.getValue("redirect"), "code=c&state=${q.getValue("state")}")
                    true
                })

            dataSource.obtainOneTimeCode(OAuthProvider.DISCORD)

            assertTrue(requireNotNull(openedUrl).startsWith("https://marketplace.example.com/auth/discord/desktop?"))
        }

    @Test
    fun `a wrong-state request does not pre-empt a valid sign-in`() =
        runBlocking {
            val dataSource =
                SystemBrowserOAuthDataSource(config, openBrowser = { url ->
                    val q = queryOf(url)
                    // A hostile/probe local request with the wrong state must be ignored...
                    deliver(q.getValue("redirect"), "code=attacker&state=wrong-state")
                    // ...and the legitimate redirect that follows must still win.
                    deliver(q.getValue("redirect"), "code=real-code&state=${q.getValue("state")}")
                    true
                })

            val code = dataSource.obtainOneTimeCode(OAuthProvider.GOOGLE)

            assertEquals("real-code", code)
        }

    @Test
    fun `times out when no valid callback arrives`() {
        val dataSource =
            SystemBrowserOAuthDataSource(
                config,
                openBrowser = { url ->
                    val q = queryOf(url)
                    deliver(q.getValue("redirect"), "code=c&state=wrong-state")
                    true
                },
                timeout = 1.seconds,
            )

        assertThrows<OAuthAuthenticationException> {
            runBlocking { dataSource.obtainOneTimeCode(OAuthProvider.GOOGLE) }
        }
    }

    @Test
    fun `fails when the provider reports an error`() {
        val dataSource =
            SystemBrowserOAuthDataSource(config, openBrowser = { url ->
                val q = queryOf(url)
                deliver(q.getValue("redirect"), "error=access_denied&state=${q.getValue("state")}")
                true
            })

        assertThrows<OAuthAuthenticationException> {
            runBlocking { dataSource.obtainOneTimeCode(OAuthProvider.GOOGLE) }
        }
    }

    @Test
    fun `fails when the browser cannot be opened`() {
        val dataSource = SystemBrowserOAuthDataSource(config, openBrowser = { false })

        assertThrows<OAuthAuthenticationException> {
            runBlocking { dataSource.obtainOneTimeCode(OAuthProvider.GOOGLE) }
        }
    }
}
