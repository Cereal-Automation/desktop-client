package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.script.ScriptSyncManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SyncScriptsOnScriptSelectionInteractorTest {
    // ScriptSyncManager is a concrete orchestration collaborator (no repository interface and no
    // observable in-memory state of its own); mocked here purely to drive its sync result.
    private lateinit var scriptSyncManager: ScriptSyncManager
    private lateinit var interactor: SyncScriptsOnScriptSelectionInteractor

    @BeforeEach
    fun setUp() {
        scriptSyncManager = mockk()
        interactor = SyncScriptsOnScriptSelectionInteractor(scriptSyncManager)
    }

    @Test
    fun `run completes without throwing when sync reports no failures`() =
        runTest {
            coEvery { scriptSyncManager.sync(updateScripts = false) } returns emptyMap()

            interactor.run(Interactor.None())
        }

    @Test
    fun `run throws ScriptSyncException carrying the failures when sync reports errors`() =
        runTest {
            val failure = IllegalStateException("install failed")
            coEvery { scriptSyncManager.sync(updateScripts = false) } returns mapOf("com.example.one" to failure)

            val thrown =
                assertThrows<ScriptSyncException> {
                    interactor.run(Interactor.None())
                }

            assertEquals(setOf("com.example.one"), thrown.exceptions.keys)
            assertSame(failure, thrown.exceptions["com.example.one"])
        }
}
