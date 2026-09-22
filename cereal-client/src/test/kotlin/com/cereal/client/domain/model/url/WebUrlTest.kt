package com.cereal.client.domain.model.url

import com.cereal.client.domain.model.exception.InvalidWebUrlException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WebUrlTest {
    // ── WebUrl.parse ─────────────────────────────────────────────────────────

    @Test
    fun `parse accepts http and https URLs`() {
        assertNotNull(WebUrl.parse("http://example.com"))
        assertNotNull(WebUrl.parse("https://example.com/path?q=1"))
        assertNotNull(WebUrl.parse("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun `parse rejects non-web schemes`() {
        assertNull(WebUrl.parse("file:///etc/passwd"))
        assertNull(WebUrl.parse("smb://attacker/share"))
        assertNull(WebUrl.parse("javascript:alert(1)"))
        assertNull(WebUrl.parse("ftp://example.com"))
    }

    @Test
    fun `parse rejects relative or unparseable input`() {
        assertNull(WebUrl.parse(""))
        assertNull(WebUrl.parse("not a url"))
        assertNull(WebUrl.parse("/just/a/path"))
        assertNull(WebUrl.parse("example.com"))
    }

    @Test
    fun `parse preserves the original URL value`() {
        val webUrl = WebUrl.parse("https://example.com/path?q=1")
        assertNotNull(webUrl)
        assertEquals("https://example.com/path?q=1", webUrl!!.value)
        assertEquals("https://example.com/path?q=1", webUrl.toString())
    }

    @Test
    fun `parse exposes host and path`() {
        val webUrl = WebUrl.parse("https://example.com/support/issues")
        assertNotNull(webUrl)
        assertEquals("example.com", webUrl!!.host)
        assertEquals("/support/issues", webUrl.path)
    }

    @Test
    fun `path is empty string when URL has no path`() {
        val webUrl = WebUrl.parse("https://example.com")
        assertNotNull(webUrl)
        assertEquals("", webUrl!!.path)
    }

    @Test
    fun `equal URLs are value-equal`() {
        assertEquals(WebUrl.parse("https://example.com/a"), WebUrl.parse("https://example.com/a"))
    }

    // ── WebUrl.of ────────────────────────────────────────────────────────────

    @Test
    fun `of returns a WebUrl for valid input`() {
        assertEquals("https://example.com", WebUrl.of("https://example.com").value)
    }

    @Test
    fun `of throws InvalidWebUrlException for invalid input`() {
        assertThrows<InvalidWebUrlException> { WebUrl.of("file:///etc/passwd") }
        assertThrows<InvalidWebUrlException> { WebUrl.of("not a url") }
        assertThrows<InvalidWebUrlException> { WebUrl.of("") }
    }

    // ── isWebUrl shim (backed by WebUrl.parse) ───────────────────────────────

    @Test
    fun `isWebUrl returns true for http and https URLs`() {
        assertTrue(isWebUrl("http://example.com"))
        assertTrue(isWebUrl("https://example.com/path?q=1"))
        assertTrue(isWebUrl("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun `isWebUrl returns false for non-web schemes`() {
        assertFalse(isWebUrl("file:///etc/passwd"))
        assertFalse(isWebUrl("smb://attacker/share"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("ftp://example.com"))
    }

    @Test
    fun `isWebUrl returns false for relative or unparseable input`() {
        assertFalse(isWebUrl(""))
        assertFalse(isWebUrl("not a url"))
        assertFalse(isWebUrl("/just/a/path"))
        assertFalse(isWebUrl("example.com"))
    }
}
