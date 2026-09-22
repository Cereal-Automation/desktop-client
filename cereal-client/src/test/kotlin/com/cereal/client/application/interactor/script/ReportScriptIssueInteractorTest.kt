package com.cereal.client.application.interactor.script

import com.cereal.client.application.script.GitHubIssueUrlBuilder
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportScriptIssueInteractorTest {
    // SystemProvider is the in-memory recording repository under test; the ScriptPackageInstance
    // graph below stays a domain-model fixture (it is not a repository).
    private val systemRepository = InMemorySystemProvider()
    private val gitHubIssueUrlBuilder = GitHubIssueUrlBuilder()
    private val interactor = ReportScriptIssueInteractor(systemRepository, gitHubIssueUrlBuilder)

    @Test
    fun `run should open browser with GitHub issue URL when supportUrl is a GitHub issues URL`() =
        runTest {
            val params =
                ReportScriptIssueInteractor.Params(
                    scriptPackageInstance = aScriptPackageInstance("https://github.com/owner/repo/issues"),
                    errorMessage = "Something broke",
                    stackTrace = null,
                )

            interactor.run(params)

            assertEquals(1, systemRepository.browsedUrls.size)
            assertTrue(systemRepository.browsedUrls.single().startsWith("https://github.com/owner/repo/issues/new"))
        }

    @Test
    fun `run should open bare supportUrl when it is not a GitHub issues URL`() =
        runTest {
            val supportUrl = "https://example.com/support"
            val params =
                ReportScriptIssueInteractor.Params(
                    scriptPackageInstance = aScriptPackageInstance(supportUrl),
                    errorMessage = "Oops",
                    stackTrace = null,
                )

            interactor.run(params)

            assertEquals(listOf(supportUrl), systemRepository.browsedUrls)
        }

    @Test
    fun `run should not open browser when supportUrl is null`() =
        runTest {
            val params =
                ReportScriptIssueInteractor.Params(
                    scriptPackageInstance = aScriptPackageInstance(supportUrl = null),
                    errorMessage = "Oops",
                    stackTrace = null,
                )

            interactor.run(params)

            assertTrue(systemRepository.browsedUrls.isEmpty())
        }

    private fun aScriptPackageInstance(supportUrl: String?): ScriptPackageInstance {
        val manifest = mockk<Manifest>(relaxed = true)
        every { manifest.supportUrl } returns supportUrl
        every { manifest.name } returns "Test Script"
        every { manifest.versionCode } returns 1L
        val scriptPackage = mockk<ScriptPackage>(relaxed = true)
        every { scriptPackage.manifest } returns manifest
        return mockk<ScriptPackageInstance>(relaxed = true).also {
            every { it.definition } returns scriptPackage
        }
    }
}
