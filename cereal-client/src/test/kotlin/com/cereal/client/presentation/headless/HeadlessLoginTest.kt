package com.cereal.client.presentation.headless

import com.cereal.client.application.exception.MarketplaceUnreachableException
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemorySessionRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.varabyte.kotter.foundation.input.Keys
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.dsl.module
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import kotlin.time.Duration.Companion.seconds

class HeadlessLoginTest {
    private val signedOut: suspend Koin.() -> Unit = { get<SessionRepository>().setSessionUser(null) }

    private suspend fun HeadlessTestScope.openEmailForm() {
        awaitText("Sign in to Cereal")
        press(Keys.Enter)
        awaitText("Email     _")
    }

    @Test
    fun `a stored session lands on the tabs without the login screen`() =
        runHeadlessTest {
            var sawLogin = false
            awaitScreen { lines ->
                sawLogin = sawLogin || lines.any { "Sign in to Cereal" in it }
                lines.first().contains("[1 Tasks]")
            }
            assertFalse(sawLogin)
        }

    @Test
    fun `without a stored session the login screen offers email and password and points to the marketplace`() =
        runHeadlessTest(seed = signedOut) {
            val screen = awaitText("Sign in to Cereal")

            val text = screen.joinToString("\n")
            assertTrue("› Email and password" in text)
            assertTrue("Register at " in text)
            assertTrue("Reset it at " in text)
            assertFalse("guest" in text.lowercase())
            assertFalse(screen.first().contains("Tasks"))
        }

    @Test
    fun `signing in with email and password masks the password and reaches the tabs`() =
        runHeadlessTest(seed = signedOut) {
            openEmailForm()

            type("alice@example.com")
            press(Keys.Tab)
            type("s3cret")

            val screen = awaitText("Password  ******_")
            assertTrue(screen.none { "s3cret" in it })
            assertTrue(screen.any { "Email     alice@example.com" in it })
            // q types into the field, so the footer offers Ctrl-C instead.
            assertTrue(screen.last().startsWith("Ctrl-C quit"), screen.last())

            press(Keys.Enter)
            awaitText("[1 Tasks]")
        }

    @Test
    fun `wrong credentials render inline and clear the password`() =
        runHeadlessTest(seed = {
            signedOut()
            (get<AuthProvider>() as InMemoryAuthProvider).acceptedPassword = "right"
        }) {
            openEmailForm()
            type("alice@example.com")
            press(Keys.Enter)
            type("wrong")
            press(Keys.Enter)

            val screen = awaitText("! The provided credentials are incorrect.")
            assertTrue(screen.any { it.trimEnd().endsWith("Password  _") })

            type("right")
            press(Keys.Enter)
            awaitText("[1 Tasks]")
        }

    @Test
    fun `validation errors render inline`() =
        runHeadlessTest(seed = signedOut) {
            openEmailForm()
            press(Keys.Tab)
            type("pw")
            press(Keys.Enter)

            awaitText("! The email field is required.")
        }

    @Test
    fun `an unreachable marketplace at boot retries with a visible line and never shows login`() =
        runHeadlessTest(seed = {
            val stored = get<SessionRepository>()
            loadModules(
                listOf(module { single<SessionRepository> { UnreachableThenStored(stored as InMemorySessionRepository, failures = 1) } }),
                allowOverride = true,
            )
        }) {
            var sawLogin = false
            awaitText("Can't reach the marketplace, retrying…")
            awaitScreen(timeout = 5.seconds) { lines ->
                sawLogin = sawLogin || lines.any { "Sign in to Cereal" in it }
                lines.first().contains("[1 Tasks]")
            }
            assertFalse(sawLogin)
        }

    /** Fails the first [failures] stored-session loads as unreachable, then behaves like [delegate]. */
    private class UnreachableThenStored(
        private val delegate: InMemorySessionRepository,
        private var failures: Int,
    ) : SessionRepository by delegate {
        override suspend fun getStoredUser(): User? {
            if (failures-- > 0) throw MarketplaceUnreachableException()
            return delegate.getStoredUser()
        }
    }
}
