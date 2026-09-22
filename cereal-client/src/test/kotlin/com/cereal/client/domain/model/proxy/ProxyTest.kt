package com.cereal.client.domain.model.proxy

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProxyTest {
    @Test
    fun `should create valid Proxy without authentication`() {
        val id = UUID.randomUUID()
        val proxy =
            Proxy(
                id = id,
                address = "proxy.example.com",
                port = 8080,
                username = null,
                password = null,
            )

        assertEquals(id, proxy.id)
        assertEquals("proxy.example.com", proxy.address)
        assertEquals(8080, proxy.port)
        assertEquals(null, proxy.username)
        assertEquals(null, proxy.password)
    }

    @Test
    fun `should create valid Proxy with authentication`() {
        val id = UUID.randomUUID()
        val proxy =
            Proxy(
                id = id,
                address = "192.168.1.1",
                port = 3128,
                username = "proxyuser",
                password = "proxypass",
            )

        assertEquals(id, proxy.id)
        assertEquals("192.168.1.1", proxy.address)
        assertEquals(3128, proxy.port)
        assertEquals("proxyuser", proxy.username)
        assertEquals("proxypass", proxy.password)
    }

    @Test
    fun `should throw exception when address is blank`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "",
                    port = 8080,
                    username = null,
                    password = null,
                )
            }
        assertEquals("Proxy address must not be blank", exception.message)
    }

    @Test
    fun `should throw exception when address is whitespace only`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "   ",
                    port = 8080,
                    username = null,
                    password = null,
                )
            }
        assertEquals("Proxy address must not be blank", exception.message)
    }

    @Test
    fun `should throw exception when port is 0`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 0,
                    username = null,
                    password = null,
                )
            }
        assertEquals("Port must be between 1 and 65535", exception.message)
    }

    @Test
    fun `should throw exception when port is negative`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = -1,
                    username = null,
                    password = null,
                )
            }
        assertEquals("Port must be between 1 and 65535", exception.message)
    }

    @Test
    fun `should throw exception when port exceeds 65535`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 65536,
                    username = null,
                    password = null,
                )
            }
        assertEquals("Port must be between 1 and 65535", exception.message)
    }

    @Test
    fun `should accept port 1`() {
        val proxy =
            Proxy(
                id = UUID.randomUUID(),
                address = "proxy.example.com",
                port = 1,
                username = null,
                password = null,
            )
        assertEquals(1, proxy.port)
    }

    @Test
    fun `should accept port 65535`() {
        val proxy =
            Proxy(
                id = UUID.randomUUID(),
                address = "proxy.example.com",
                port = 65535,
                username = null,
                password = null,
            )
        assertEquals(65535, proxy.port)
    }

    @Test
    fun `should throw exception when username is provided but password is null`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 8080,
                    username = "proxyuser",
                    password = null,
                )
            }
        assertEquals("Username and password must both be provided or both be null", exception.message)
    }

    @Test
    fun `should throw exception when password is provided but username is null`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 8080,
                    username = null,
                    password = "proxypass",
                )
            }
        assertEquals("Username and password must both be provided or both be null", exception.message)
    }

    @Test
    fun `should throw exception when username is blank`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 8080,
                    username = "",
                    password = "proxypass",
                )
            }
        assertEquals("Username must not be blank if provided", exception.message)
    }

    @Test
    fun `should throw exception when username is whitespace only`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 8080,
                    username = "   ",
                    password = "proxypass",
                )
            }
        assertEquals("Username must not be blank if provided", exception.message)
    }

    @Test
    fun `should throw exception when password is blank`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 8080,
                    username = "proxyuser",
                    password = "",
                )
            }
        assertEquals("Password must not be blank if provided", exception.message)
    }

    @Test
    fun `should throw exception when password is whitespace only`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                Proxy(
                    id = UUID.randomUUID(),
                    address = "proxy.example.com",
                    port = 8080,
                    username = "proxyuser",
                    password = "   ",
                )
            }
        assertEquals("Password must not be blank if provided", exception.message)
    }
}
