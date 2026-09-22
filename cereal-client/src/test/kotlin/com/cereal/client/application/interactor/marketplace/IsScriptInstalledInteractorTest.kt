package com.cereal.client.application.interactor.marketplace

import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class IsScriptInstalledInteractorTest {
    private lateinit var scriptRepository: InMemoryScriptRepository
    private lateinit var interactor: IsScriptInstalledInteractor

    @BeforeEach
    fun setUp() {
        scriptRepository = InMemoryScriptRepository()
        interactor = IsScriptInstalledInteractor(scriptRepository)
    }

    @Test
    fun `run returns true when script exists in repository`() =
        runTest {
            scriptRepository.seed(listOf(aScriptPackage("com.example.script")))

            val result = interactor.run(IsScriptInstalledInteractor.Params("com.example.script"))

            assertTrue(result)
        }

    @Test
    fun `run returns false when script does not exist in repository`() =
        runTest {
            val result = interactor.run(IsScriptInstalledInteractor.Params("com.example.missing-script"))

            assertFalse(result)
        }

    @Test
    fun `run resolves installation by the given publicIdentifier`() =
        runTest {
            scriptRepository.seed(listOf(aScriptPackage("com.cereal.my-script")))

            assertTrue(interactor.run(IsScriptInstalledInteractor.Params("com.cereal.my-script")))
            assertFalse(interactor.run(IsScriptInstalledInteractor.Params("com.cereal.other-script")))
        }

    // ScriptPackage is a domain-model fixture; only its non-repository MainScript is stubbed.
    private fun aScriptPackage(packageName: String) =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest =
                Manifest(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(relaxed = true),
            childScripts = emptyMap(),
        )
}
