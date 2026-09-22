package com.cereal.client.application.interactor.files

import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReadProxyFileInteractorTest {
    private val manifest =
        Manifest(
            packageName = "com.example.script",
            name = "Proxy Script",
            versionCode = 1L,
        )

    @Test
    fun `should create a proxy group with parsed proxies from the file`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryProxyRepository()
        val file = File(tempDir, "proxies.txt")
        file.writeText("1.1.1.1:8080:user:pass\n2.2.2.2:9090:user2:pass2")

        val interactor = ReadProxyFileInteractor(repository)

        val result = interactor.run(ReadProxyFileInteractor.Params(file = file, manifest = manifest))

        assertEquals(2, result.group.numberOfItems)
        assertEquals(
            2,
            result.group.items
                .toList()
                .size,
        )
        assertTrue(result.group.name.startsWith("Imported for Proxy Script at "))

        val storedGroups = repository.getProxyGroups().first()
        assertEquals(listOf(result.group.id), storedGroups.map { it.id })
    }

    @Test
    fun `should create an empty group when the file has no proxies`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryProxyRepository()
        val file = File(tempDir, "empty.txt")
        file.writeText("")

        val interactor = ReadProxyFileInteractor(repository)

        val result = interactor.run(ReadProxyFileInteractor.Params(file = file, manifest = manifest))

        assertEquals(0, result.group.numberOfItems)
        val storedGroups = repository.getProxyGroups().first()
        assertEquals(listOf(result.group.id), storedGroups.map { it.id })
    }

    @Test
    fun `should throw when the file content is invalid`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryProxyRepository()
        val file = File(tempDir, "invalid.txt")
        file.writeText("1.1.1.1:notaport:user:pass")

        val interactor = ReadProxyFileInteractor(repository)

        assertThrows<InvalidFileException> {
            interactor.run(ReadProxyFileInteractor.Params(file = file, manifest = manifest))
        }

        assertEquals(emptyList<Any>(), repository.getProxyGroups().first())
    }
}
