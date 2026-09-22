package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import fixtures.aScriptPackage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(FlowPreview::class)
class GetScriptsInteractorTest {
    private lateinit var scriptRepository: InMemoryScriptRepository
    private lateinit var interactor: GetScriptsInteractor

    @BeforeEach
    fun setUp() {
        scriptRepository = InMemoryScriptRepository()
        interactor = GetScriptsInteractor(scriptRepository)
    }

    @Test
    fun `run emits empty list when no scripts installed`() =
        runTest {
            val result = interactor.run(Interactor.None()).first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `run emits the installed scripts seeded in the repository`() =
        runTest {
            val scripts =
                listOf(
                    aScriptPackage("com.example.one"),
                    aScriptPackage("com.example.two"),
                )
            scriptRepository.seed(scripts)

            val result = interactor.run(Interactor.None()).first()

            assertEquals(2, result.size)
            assertEquals(
                listOf("com.example.one", "com.example.two"),
                result.map { it.manifest.packageName },
            )
        }
}
