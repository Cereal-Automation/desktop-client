package com.cereal.client.application.interactor.script

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import fixtures.aScriptPackageInstance
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetScriptPackageInstancesByPackageNameInteractorTest {
    private lateinit var scriptInstanceRepository: InMemoryScriptInstanceRepository
    private lateinit var interactor: GetScriptPackageInstancesByPackageNameInteractor

    @BeforeEach
    fun setUp() {
        scriptInstanceRepository = InMemoryScriptInstanceRepository()
        interactor = GetScriptPackageInstancesByPackageNameInteractor(scriptInstanceRepository)
    }

    @Test
    fun `run returns empty list when no instances exist`() =
        runTest {
            val result =
                interactor.run(
                    GetScriptPackageInstancesByPackageNameInteractor.Params(packageName = "com.example.one"),
                )

            assertTrue(result.isEmpty())
        }

    @Test
    fun `run returns the package instances held by the repository`() =
        runTest {
            val instances =
                listOf(
                    aScriptPackageInstance(id = "instance-1", packageName = "com.example.one"),
                    aScriptPackageInstance(id = "instance-2", packageName = "com.example.one"),
                )
            scriptInstanceRepository.seed(instances)

            val result =
                interactor.run(
                    GetScriptPackageInstancesByPackageNameInteractor.Params(packageName = "com.example.one"),
                )

            assertEquals(2, result.size)
            assertEquals(listOf("instance-1", "instance-2"), result.map { it.id })
        }
}
