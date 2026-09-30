package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.service.ProxyHealthChecker
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.runtime.terminal.TerminalSize
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class HeadlessProxiesTest {
    private fun proxy(
        address: String,
        health: ProxyHealth = ProxyHealth.Unknown,
    ) = Proxy(UUID.randomUUID(), address, 8080, null, null, health)

    private val main =
        listOf(
            proxy("10.0.0.1", ProxyHealth.healthy(Clock.System.now(), 40)),
            proxy("10.0.0.2", ProxyHealth.failed(Clock.System.now(), "timed out")),
            proxy("bad.example"),
        ).let { ProxyGroup("g-main", "Main", it.size, it.asSequence()) }

    /** Fails every proxy whose address starts with "bad". */
    private val checker =
        object : ProxyHealthChecker {
            override suspend fun check(proxy: Proxy) = if (proxy.address.startsWith("bad")) ProxyHealth.failed(Clock.System.now(), "refused") else ProxyHealth.healthy(Clock.System.now(), 5)
        }

    private fun proxiesTest(block: suspend HeadlessTestScope.() -> Unit) =
        // Wide enough for a temp file's absolute path.
        runHeadlessTest(
            size = TerminalSize(200, 24),
            seed = {
                loadModules(
                    listOf(
                        module {
                            single<ProxyRepository> { InMemoryProxyRepository(listOf(main)) }
                            single<ProxyHealthChecker> { checker }
                        },
                    ),
                    allowOverride = true,
                )
            },
        ) {
            awaitText("[1 Tasks]")
            press(CharKey('3'))
            awaitText("Main  3 proxies, 1 failing")
            block()
        }

    private suspend fun HeadlessTestScope.paste(vararg lines: String) {
        lines.forEachIndexed { i, line ->
            if (i > 0) press(Keys.Enter)
            type(line)
            awaitText("│ ${line}_")
        }
        press(Keys.Eof)
    }

    private suspend fun HeadlessTestScope.proxies(name: String) =
        get<ProxyRepository>().let { repo ->
            repo.getProxiesFromGroup(
                repo
                    .getProxyGroups()
                    .first()
                    .first { it.name == name }
                    .id,
            )
        }

    @Test
    fun `a group is created from a paste and nothing is created from a bad one`() =
        proxiesTest {
            press(CharKey('a'))
            awaitText("Name: _")
            type("Fresh")
            awaitText("Name: Fresh_")
            press(Keys.Enter)
            awaitText("› Paste a proxy list")
            press(Keys.Enter)
            paste("10.1.0.1:8080", "10.1.0.2:http")
            awaitText("! Line 2: The port must be a valid number")
            assertEquals(listOf("Main"), get<ProxyRepository>().getProxyGroups().first().map { it.name })

            // The paste is kept for fixing.
            listOf("htt", "ht", "h", "").forEach {
                press(Keys.Backspace)
                awaitText("│ 10.1.0.2:${it}_")
            }
            type("3128")
            awaitText("│ 10.1.0.2:3128_")
            press(Keys.Eof)
            awaitText("Fresh: 2 proxies, 0 failing")
            awaitText("Imported 2 proxy(ies).")
            assertEquals(listOf("10.1.0.1", "10.1.0.2"), proxies("Fresh").map { it.address })

            press(Keys.Escape)
            awaitText("Fresh  2 proxies, 0 failing")
        }

    @Test
    fun `a file in the volume appends to a group`() {
        val file = File.createTempFile("proxies", ".txt").apply { writeText("10.2.0.1:8080:user:pass\n") }
        try {
            proxiesTest {
                press(Keys.Enter)
                awaitText("Main: 3 proxies, 1 failing")
                press(CharKey('a'))
                awaitText("Append to Main")
                press(Keys.Down)
                awaitText("› A file in the data volume")
                press(Keys.Enter)
                awaitText("Path in the data volume:")
                type(file.absolutePath)
                awaitText("${file.name}_")
                press(Keys.Enter)
                awaitText("Main: 4 proxies, 1 failing")
                awaitText("10.2.0.1:8080  user")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `test all updates health and delete failing removes the failing proxies`() =
        proxiesTest {
            press(Keys.Enter)
            awaitText("Main: 3 proxies, 1 failing")
            press(CharKey('t'))
            awaitText("Tested 3 proxy(ies): 1 failing.")
            awaitText("FAILED: refused")
            // 10.0.0.2 recovered, bad.example now fails.
            awaitText("Main: 3 proxies, 1 failing")

            press(CharKey('D'))
            awaitText("Delete the 1 failing proxy(ies)? It can't be undone. [y/N]")
            press(CharKey('n'))
            awaitScreen { s -> s.none { "[y/N]" in it } }
            press(CharKey('D'))
            awaitText("[y/N]")
            press(CharKey('y'))
            awaitText("Deleted 1 failing proxy(ies).")
            awaitText("Main: 2 proxies, 0 failing")
            assertEquals(listOf("10.0.0.1", "10.0.0.2"), proxies("Main").map { it.address })
        }
}
