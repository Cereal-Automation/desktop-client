package com.cereal.client.infrastructure.data.repository.inmemory

import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryScriptInstanceRepositoryTest {
    private val repository = InMemoryScriptInstanceRepository()
    private val first = aScriptPackageInstance("instance-1", "com.example.one")
    private val second = aScriptPackageInstance("instance-2", "com.example.two")

    @Test
    fun `instances are listed per group they were added to`() =
        runTest {
            repository.addScriptPackageInstance("group-a", first, aScriptInstance(first))
            repository.addScriptPackageInstance("group-b", second, aScriptInstance(second))

            assertEquals(listOf(first), repository.getScriptPackageInstancesInGroupFlow("group-a").first())
            assertEquals(listOf(second), repository.getScriptPackageInstancesInGroupFlow("group-b").first())
        }

    @Test
    fun `moving an instance changes the group it is listed in`() =
        runTest {
            repository.addScriptPackageInstance("group-a", first, aScriptInstance(first))

            repository.updateScriptPackageInstanceGroup(first, "group-b")

            assertEquals(emptyList<Any>(), repository.getScriptPackageInstancesInGroupFlow("group-a").first())
            assertEquals(listOf(first), repository.getScriptPackageInstancesInGroupFlow("group-b").first())
        }

    @Test
    fun `script instances of a package instance include its main script`() =
        runTest {
            val main = aScriptInstance(first)
            repository.addScriptPackageInstance("group-a", first, main)

            assertEquals(listOf(main), repository.getScriptInstances(first))
        }

    @Test
    fun `seeded instances land in the given group with a main script matching the task fixtures`() =
        runTest {
            repository.seed(listOf(first), groupId = "group-a")

            assertEquals(listOf(first), repository.getScriptPackageInstancesInGroupFlow("group-a").first())
            assertEquals(listOf(aScriptInstance(first).id), repository.getScriptInstances(first).map { it.id })
        }
}
