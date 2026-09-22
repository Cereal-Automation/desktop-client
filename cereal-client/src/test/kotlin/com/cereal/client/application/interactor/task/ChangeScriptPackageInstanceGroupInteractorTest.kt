package com.cereal.client.application.interactor.task

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import fixtures.aScriptPackageInstance
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChangeScriptPackageInstanceGroupInteractorTest {
    /**
     * The shared [InMemoryScriptInstanceRepository] does not model grouping, so its
     * updateScriptPackageInstanceGroup is a no-op with no observable state. We delegate to it for
     * everything else and record the move so the observable effect can be asserted.
     */
    private class RecordingScriptInstanceRepository(
        private val delegate: InMemoryScriptInstanceRepository = InMemoryScriptInstanceRepository(),
    ) : ScriptInstanceRepository by delegate {
        val moves = mutableListOf<Pair<String, String>>()

        override suspend fun updateScriptPackageInstanceGroup(
            scriptPackageInstance: ScriptPackageInstance,
            newGroupId: String,
        ) {
            moves.add(scriptPackageInstance.id to newGroupId)
        }
    }

    @Test
    fun `run should move the script package instance to the new group`() =
        runTest {
            val repository = RecordingScriptInstanceRepository()
            val interactor = ChangeScriptPackageInstanceGroupInteractor(repository)
            val instance = aScriptPackageInstance("instance-1", "com.example.one")

            interactor.run(
                ChangeScriptPackageInstanceGroupInteractor.Params(
                    scriptPackageInstance = instance,
                    newGroupId = "group-2",
                ),
            )

            assertEquals(listOf("instance-1" to "group-2"), repository.moves)
        }

    @Test
    fun `run should propagate repository failures`() =
        runTest {
            val repository = mockk<ScriptInstanceRepository>(relaxed = true)
            val interactor = ChangeScriptPackageInstanceGroupInteractor(repository)
            val instance = aScriptPackageInstance("instance-1", "com.example.one")
            coEvery {
                repository.updateScriptPackageInstanceGroup(instance, "group-2")
            } throws IllegalStateException("db error")

            assertThrows<IllegalStateException> {
                interactor.run(
                    ChangeScriptPackageInstanceGroupInteractor.Params(
                        scriptPackageInstance = instance,
                        newGroupId = "group-2",
                    ),
                )
            }
        }
}
