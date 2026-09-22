package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.InvalidLoginCredentialsException
import com.cereal.client.application.exception.LoginValidationException
import com.cereal.client.application.exception.OAuthAuthenticationException
import com.cereal.client.application.exception.RegistrationValidationException
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.FakeMarketplaceDataSource
import com.cereal.client.fixtures.FakeSubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.OAuthDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.LoginResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Release
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User as ApiUser

class AuthProviderImplTest {
    private val marketplaceDataSource = FakeMarketplaceDataSource()
    private val subscriptionDataSource = FakeSubscriptionDataSource()
    private val userSession = mockk<UserSession>(relaxed = true)
    private val oauthDataSource = mockk<OAuthDataSource>(relaxed = true)
    private lateinit var provider: AuthProviderImpl

    @BeforeEach
    fun setUp() {
        provider =
            AuthProviderImpl(
                marketplaceDataSource = marketplaceDataSource,
                userSession = userSession,
                subscriptionDataSource = subscriptionDataSource,
                oauthDataSource = oauthDataSource,
            )
    }

    private fun apiUser(
        id: String = "user-1",
        name: String = "Alice",
        email: String = "alice@example.com",
        key: String = "encryption-key",
        isGuest: Boolean = false,
    ) = ApiUser(id = id, name = name, email = email, key = key, isGuest = isGuest)

    private fun storeScriptListing(
        publicIdentifier: String = "com.test.script",
        title: String = "Test Script",
    ) = ScriptEntitlement(
        publicIdentifier = publicIdentifier,
        title = title,
        latestRelease = null,
        latestDraftRelease = null,
        shortDescription = null,
        price = null,
    )

    @Test
    fun `authenticate maps login response to domain user`() =
        runTest {
            marketplaceDataSource.loginResponse =
                LoginResponse(token = "token-123", user = apiUser())

            val result = provider.authenticate("alice@example.com", "secret")

            assertEquals("user-1", result.id)
            assertEquals("Alice", result.name)
            assertEquals("alice@example.com", result.email)
            assertEquals("encryption-key", result.encryptionKey)
            assertEquals("token-123", result.accessToken)
            assertEquals(false, result.isGuest)
        }

    @Test
    fun `authenticateGuest maps login response to domain user`() =
        runTest {
            marketplaceDataSource.loginResponse =
                LoginResponse(token = "guest-token", user = apiUser(id = "guest-1", isGuest = true))

            val result = provider.authenticateGuest()

            assertEquals("guest-1", result.id)
            assertEquals("guest-token", result.accessToken)
            assertEquals(true, result.isGuest)
        }

    @Test
    fun `authenticateWith brokers a code and maps the exchange response to a domain user`() =
        runTest {
            coEvery { oauthDataSource.obtainOneTimeCode(OAuthProvider.DISCORD) } returns "one-time-code"
            marketplaceDataSource.loginResponse =
                LoginResponse(token = "sso-token", user = apiUser(id = "user-9", name = "Dana"))

            val result = provider.authenticateWith(OAuthProvider.DISCORD)

            assertEquals("user-9", result.id)
            assertEquals("Dana", result.name)
            assertEquals("sso-token", result.accessToken)
        }

    @Test
    fun `authenticateWith translates AuthenticationException to OAuthAuthenticationException`() =
        runTest {
            // MockK: an invalid/expired one-time code surfaces as a rejected exchange.
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.exchangeOAuthCode(any(), any()) } throws AuthenticationException()
            coEvery { oauthDataSource.obtainOneTimeCode(any()) } returns "code"
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            assertFailsWith<OAuthAuthenticationException> {
                authProvider.authenticateWith(OAuthProvider.GOOGLE)
            }
        }

    @Test
    fun `register maps login response to domain user`() =
        runTest {
            marketplaceDataSource.loginResponse =
                LoginResponse(token = "reg-token", user = apiUser(id = "user-2", name = "Bob"))

            val result = provider.register("Bob", "bob@example.com", "secret")

            assertEquals("user-2", result.id)
            assertEquals("Bob", result.name)
            assertEquals("reg-token", result.accessToken)
        }

    @Test
    fun `getSubscriptions returns subscriptions from data source`() =
        runTest {
            val subscriptions = listOf(Subscription(id = "sub-1", entitlement = storeScriptListing()))
            subscriptionDataSource.subscriptions = subscriptions

            val result = provider.getSubscriptions(ignoreCache = false)

            assertEquals(subscriptions, result)
            assertEquals(0, subscriptionDataSource.invalidateCacheCallCount)
        }

    @Test
    fun `getSubscriptions invalidates cache before fetching when ignoreCache is true`() =
        runTest {
            val subscriptions = listOf(Subscription(id = "sub-1", entitlement = storeScriptListing()))
            subscriptionDataSource.subscriptions = subscriptions

            val result = provider.getSubscriptions(ignoreCache = true)

            assertEquals(subscriptions, result)
            assertEquals(1, subscriptionDataSource.invalidateCacheCallCount)
        }

    @Test
    fun `invalidateSubscriptionsCache invalidates the subscription cache`() =
        runTest {
            provider.invalidateSubscriptionsCache()

            assertEquals(1, subscriptionDataSource.invalidateCacheCallCount)
        }

    @Test
    fun `getMyTeamScripts maps team scripts to domain listings`() =
        runTest {
            coEvery { userSession.requireUser() } returns User("team-user", "n", "e", "k", "t", false)
            marketplaceDataSource.myTeamScripts =
                listOf(
                    Script(
                        publicIdentifier = "com.test.script",
                        title = "Test Script",
                        latestRelease = Release("1.0.0", 1L, "notes"),
                        latestDraftRelease = null,
                        shortDescription = "short",
                        price = BigDecimal("9.99"),
                    ),
                )

            val result = provider.getMyTeamScripts(ignoreCache = false)

            assertEquals(1, result.size)
            val listing = result.first()
            assertEquals("com.test.script", listing.publicIdentifier)
            assertEquals("Test Script", listing.title)
            assertEquals("1.0.0", listing.latestRelease?.versionName)
            assertEquals(1L, listing.latestRelease?.versionCode)
            assertEquals("notes", listing.latestRelease?.releaseNotes)
            assertNull(listing.latestDraftRelease)
            assertEquals("short", listing.shortDescription)
            assertEquals(BigDecimal("9.99"), listing.price)
        }

    @Test
    fun `getMyTeamScripts maps the script capacity pair into ScriptCapacity`() =
        runTest {
            coEvery { userSession.requireUser() } returns User("team-user", "n", "e", "k", "t", false)
            marketplaceDataSource.myTeamScripts =
                listOf(
                    Script(
                        publicIdentifier = "com.test.tiered",
                        title = "Tiered Script",
                        latestRelease = null,
                        latestDraftRelease = null,
                        shortDescription = null,
                        price = null,
                        capacity = 750,
                        capacityUnit = "records",
                    ),
                )

            val listing = provider.getMyTeamScripts(ignoreCache = false).single()

            assertEquals(ScriptCapacity.Limited(750, "records"), listing.capacity)
        }

    @Test
    fun `getMyTeamScripts returns the cached result on the second call`() =
        runTest {
            coEvery { userSession.requireUser() } returns User("team-user", "n", "e", "k", "t", false)
            marketplaceDataSource.myTeamScripts =
                listOf(Script("com.first.script", "First", null, null, null, null))

            val first = provider.getMyTeamScripts(ignoreCache = false)

            // Change the underlying source; a cached read must not observe the change.
            marketplaceDataSource.myTeamScripts =
                listOf(Script("com.second.script", "Second", null, null, null, null))
            val second = provider.getMyTeamScripts(ignoreCache = false)

            assertSame(first, second)
            assertEquals("com.first.script", second.first().publicIdentifier)
        }

    @Test
    fun `getMyTeamScripts bypasses the cache when ignoreCache is true`() =
        runTest {
            coEvery { userSession.requireUser() } returns User("team-user", "n", "e", "k", "t", false)
            marketplaceDataSource.myTeamScripts =
                listOf(Script("com.first.script", "First", null, null, null, null))

            val first = provider.getMyTeamScripts(ignoreCache = false)

            marketplaceDataSource.myTeamScripts =
                listOf(Script("com.second.script", "Second", null, null, null, null))
            val fresh = provider.getMyTeamScripts(ignoreCache = true)

            assertNotSame(first, fresh)
            assertEquals("com.second.script", fresh.first().publicIdentifier)
        }

    @Test
    fun `forgotPassword forwards the email to the marketplace data source`() =
        runTest {
            provider.forgotPassword("alice@example.com")

            assertEquals(listOf("alice@example.com"), marketplaceDataSource.forgotPasswordEmails)
        }

    @Test
    fun `authenticate translates AuthenticationException to InvalidLoginCredentialsException`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.authenticate(any()) } throws AuthenticationException()
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            assertFailsWith<InvalidLoginCredentialsException> {
                authProvider.authenticate("alice@example.com", "wrong")
            }
        }

    @Test
    fun `authenticate translates ApiException with validation errors to LoginValidationException`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.authenticate(any()) } throws
                ApiException("bad", validationErrors = listOf("The email field is required.", "The password field is required."))
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            val exception =
                assertFailsWith<LoginValidationException> {
                    authProvider.authenticate("", "")
                }
            assertEquals("The email field is required.\nThe password field is required.", exception.message)
        }

    @Test
    fun `authenticate rethrows ApiException when there are no validation errors`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.authenticate(any()) } throws ApiException("server error", validationErrors = null)
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            assertFailsWith<ApiException> {
                authProvider.authenticate("alice@example.com", "secret")
            }
        }

    @Test
    fun `authenticate rethrows ApiException when the validation errors list is empty`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.authenticate(any()) } throws ApiException("server error", validationErrors = emptyList())
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            assertFailsWith<ApiException> {
                authProvider.authenticate("alice@example.com", "secret")
            }
        }

    @Test
    fun `register translates ApiException with validation errors to RegistrationValidationException`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.register(any()) } throws
                ApiException("bad", validationErrors = listOf("Email taken", "Name invalid"))
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            val exception =
                assertFailsWith<RegistrationValidationException> {
                    authProvider.register("Bob", "bob@example.com", "secret")
                }
            assertEquals("Email taken\nName invalid", exception.message)
        }

    @Test
    fun `register rethrows ApiException when there are no validation errors`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.register(any()) } throws ApiException("server error", validationErrors = null)
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            assertFailsWith<ApiException> {
                authProvider.register("Bob", "bob@example.com", "secret")
            }
        }

    @Test
    fun `register rethrows ApiException when the validation errors list is empty`() =
        runTest {
            // MockK: inject external failure to verify translation
            val failing = mockk<MarketplaceDataSource>(relaxed = true)
            coEvery { failing.register(any()) } throws ApiException("server error", validationErrors = emptyList())
            val authProvider =
                AuthProviderImpl(
                    marketplaceDataSource = failing,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                    oauthDataSource = oauthDataSource,
                )

            assertFailsWith<ApiException> {
                authProvider.register("Bob", "bob@example.com", "secret")
            }
        }
}
