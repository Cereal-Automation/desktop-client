package com.cereal.client.infrastructure.data.datasource.discord

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class DiscordRpcDataSourceTest {
    /**
     * Reproduces the production crash where logging out and logging back in (without restarting the
     * app) threw `RejectedExecutionException`. The data source is an app-wide singleton, so `close()`
     * on logout shut down its `ScheduledExecutorService`; a `ScheduledThreadPoolExecutor` cannot be
     * restarted, so the next `initialize()` scheduled `Discord_RunCallbacks` onto a terminated
     * executor. Each `initialize()` must start from a fresh executor.
     */
    @Test
    fun `initialize after close reschedules callbacks without throwing`() {
        val discordRpc = mockk<DiscordRPC>(relaxed = true)
        val dataSource =
            DiscordRpcDataSource(
                discordRpc = discordRpc,
                discordEventHandlers = DiscordEventHandlers(),
                executorServiceFactory = { Executors.newSingleThreadScheduledExecutor() },
            )

        // login -> logout -> login on the same singleton instance
        dataSource.initialize()
        dataSource.close()

        assertDoesNotThrow { dataSource.initialize() }

        dataSource.close()
    }
}
