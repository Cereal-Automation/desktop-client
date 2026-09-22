package com.cereal.client.presentation.authenticate.login

import com.cereal.client.application.auth.UserAuthenticatingState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SetupProgressTest {
    @Test
    fun `sync scripts maps to a count when total is known`() {
        val progress = UserAuthenticatingState.SyncScripts(completed = 3, total = 12).toSetupProgress()

        assertEquals(3, progress.current)
        assertEquals(12, progress.total)
    }

    @Test
    fun `sync scripts with unknown total shows no count`() {
        val progress = UserAuthenticatingState.SyncScripts(completed = 0, total = 0).toSetupProgress()

        assertNull(progress.current)
        assertNull(progress.total)
    }

    @Test
    fun `discord phase shows only the steady headline`() {
        val progress = UserAuthenticatingState.InitializingDiscord.toSetupProgress()

        assertNull(progress.current)
        assertNull(progress.total)
    }

    @Test
    fun `restore tasks phase shows only the steady headline`() {
        val progress = UserAuthenticatingState.RestoreTasks.toSetupProgress()

        assertNull(progress.current)
        assertNull(progress.total)
    }
}
