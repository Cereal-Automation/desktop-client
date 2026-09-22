package com.cereal.client.application.interactor.files

import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AddProxiesFromFileToGroupInteractorTest {
    private fun group(id: String) = ProxyGroup(id, "Group $id", 0, emptySequence())

    @Test
    fun `should add parsed proxies from the file to the group`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryProxyRepository()
        val target = group("group-1")
        repository.createProxyGroup(target)
        val file = File(tempDir, "proxies.txt")
        file.writeText("1.1.1.1:8080:user:pass\n2.2.2.2:9090:user2:pass2")

        val interactor = AddProxiesFromFileToGroupInteractor(repository)

        val result = interactor.run(AddProxiesFromFileToGroupInteractor.Params(file = file, group = target))

        assertEquals(2, result.group.numberOfItems)
        assertEquals(2, repository.getProxiesFromGroup("group-1").size)
    }

    @Test
    fun `should report zero items when the file is empty`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryProxyRepository()
        val target = group("group-1")
        repository.createProxyGroup(target)
        val file = File(tempDir, "empty.txt")
        file.writeText("")

        val interactor = AddProxiesFromFileToGroupInteractor(repository)

        val result = interactor.run(AddProxiesFromFileToGroupInteractor.Params(file = file, group = target))

        assertEquals(0, result.group.numberOfItems)
        assertEquals(0, repository.getProxiesFromGroup("group-1").size)
    }

    @Test
    fun `should throw and add nothing when the file content is invalid`(
        @TempDir tempDir: File,
    ) = runTest {
        val repository = InMemoryProxyRepository()
        val target = group("group-1")
        repository.createProxyGroup(target)
        val file = File(tempDir, "invalid.txt")
        file.writeText("1.1.1.1:notaport:user:pass")

        val interactor = AddProxiesFromFileToGroupInteractor(repository)

        assertThrows<InvalidFileException> {
            interactor.run(AddProxiesFromFileToGroupInteractor.Params(file = file, group = target))
        }

        assertEquals(0, repository.getProxiesFromGroup("group-1").size)
    }
}
