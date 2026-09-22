package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandboxAuthProviderTest {
    private lateinit var scriptRepository: InMemoryScriptRepository
    private lateinit var provider: SandboxAuthProvider

    @BeforeEach
    fun setUp() {
        scriptRepository = InMemoryScriptRepository()
        provider = SandboxAuthProvider(scriptRepository)
    }

    private fun assertMockUser(user: User) {
        assertEquals("noreply@cereal-automation.com", user.email)
        assertEquals("Cereal test user", user.name)
        assertEquals("mock-token", user.accessToken)
        assertTrue(user.isGuest)
    }

    private fun scriptPackage(
        packageName: String,
        name: String = "Example Script",
        versionCode: Long = 7L,
        supportUrl: String? = "https://support.example.com",
    ): ScriptPackage {
        // Constructing a real ScriptPackage requires a loaded script KClass; the provider only
        // reads the manifest, so stub a relaxed package whose manifest is a real domain object.
        val pkg = mockk<ScriptPackage>(relaxed = true)
        every { pkg.manifest } returns
            Manifest(
                packageName = packageName,
                name = name,
                versionCode = versionCode,
                supportUrl = supportUrl,
            )
        return pkg
    }

    @Test
    fun `authenticate returns mock user`() =
        runTest {
            assertMockUser(provider.authenticate("a@b.com", "password"))
        }

    @Test
    fun `authenticateGuest returns mock user`() =
        runTest {
            assertMockUser(provider.authenticateGuest())
        }

    @Test
    fun `register returns mock user`() =
        runTest {
            assertMockUser(provider.register("Name", "a@b.com", "password"))
        }

    @Test
    fun `getSubscriptions maps installed scripts to subscriptions`() =
        runTest {
            scriptRepository.seed(listOf(scriptPackage("com.example.script")))

            val subscriptions = provider.getSubscriptions(ignoreCache = false)

            assertEquals(1, subscriptions.size)
            val subscription = subscriptions.single()
            assertEquals("sandbox_com.example.script", subscription.id)
            assertEquals("com.example.script", subscription.entitlement.publicIdentifier)
            assertEquals("Example Script", subscription.entitlement.title)
            assertEquals("https://support.example.com", subscription.entitlement.supportUrl)
            assertEquals(7L, subscription.entitlement.latestRelease?.versionCode)
            assertEquals("7", subscription.entitlement.latestRelease?.versionName)
            assertNull(subscription.entitlement.latestRelease?.releaseNotes)
            assertNull(subscription.entitlement.latestDraftRelease)
            assertNull(subscription.entitlement.price)
            assertNull(subscription.entitlement.shortDescription)
        }

    @Test
    fun `getSubscriptions maps every installed script`() =
        runTest {
            scriptRepository.seed(
                listOf(
                    scriptPackage("com.example.one", name = "One"),
                    scriptPackage("com.example.two", name = "Two"),
                ),
            )

            val ids = provider.getSubscriptions(ignoreCache = false).map { it.id }.toSet()

            assertEquals(setOf("sandbox_com.example.one", "sandbox_com.example.two"), ids)
        }

    @Test
    fun `getSubscriptions returns empty when no installed scripts`() =
        runTest {
            assertEquals(emptyList(), provider.getSubscriptions(ignoreCache = true))
        }

    @Test
    fun `invalidateSubscriptionsCache is a no-op`() =
        runTest {
            provider.invalidateSubscriptionsCache()
        }

    @Test
    fun `getMyTeamScripts returns empty list`() =
        runTest {
            assertEquals(emptyList(), provider.getMyTeamScripts(ignoreCache = false))
        }

    @Test
    fun `forgotPassword is a no-op`() =
        runTest {
            provider.forgotPassword("a@b.com")
        }
}
