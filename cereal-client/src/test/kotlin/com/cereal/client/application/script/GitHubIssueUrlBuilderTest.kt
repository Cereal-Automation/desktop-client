package com.cereal.client.application.script

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLDecoder

class GitHubIssueUrlBuilderTest {
    private val builder = GitHubIssueUrlBuilder()

    @Test
    fun `build should return pre-filled GitHub new-issue URL when supportUrl is a GitHub issues URL`() {
        val result =
            builder.build(
                supportUrl = "https://github.com/owner/repo/issues",
                scriptName = "My Script",
                versionCode = 42L,
                errorMessage = "Something failed",
                stackTrace = null,
            )

        assertTrue(result.startsWith("https://github.com/owner/repo/issues/new"), "Expected GitHub new-issue URL, got: $result")
        assertTrue(result.contains("title="), "Expected title query param")
        assertTrue(result.contains("body="), "Expected body query param")
    }

    @Test
    fun `build should include script name and version in body`() {
        val result =
            builder.build(
                supportUrl = "https://github.com/owner/repo/issues",
                scriptName = "My Script",
                versionCode = 42L,
                errorMessage = "Oops",
                stackTrace = null,
            )

        // script name and version should be in body, not title
        val decoded = java.net.URLDecoder.decode(result, "UTF-8")
        assertTrue(decoded.contains("**Script:** My Script v42"), "Expected script name and version in body")
    }

    @Test
    fun `build should include stack trace in body when provided`() {
        val result =
            builder.build(
                supportUrl = "https://github.com/owner/repo/issues",
                scriptName = "Bot",
                versionCode = 1L,
                errorMessage = "Error",
                stackTrace = "java.lang.NullPointerException\n\tat com.example.Foo.bar(Foo.kt:10)",
            )

        val decoded = java.net.URLDecoder.decode(result, "UTF-8")
        assertTrue(decoded.contains("NullPointerException"), "Expected stack trace in body")
    }

    @Test
    fun `build should truncate stack trace to 3000 characters`() {
        val longTrace = "x".repeat(5000)

        val result =
            builder.build(
                supportUrl = "https://github.com/owner/repo/issues",
                scriptName = "Bot",
                versionCode = 1L,
                errorMessage = "Error",
                stackTrace = longTrace,
            )

        val decoded = URLDecoder.decode(result, "UTF-8")
        assertTrue(decoded.contains("...[truncated]"), "Expected truncation marker in body")
        assertTrue(!decoded.contains("x".repeat(3001)), "Stack trace should be capped at 3000 chars")
    }

    @Test
    fun `build should return bare supportUrl when GitHub URL does not contain issues path`() {
        val supportUrl = "https://github.com/owner/repo"

        val result =
            builder.build(
                supportUrl = supportUrl,
                scriptName = "Bot",
                versionCode = 1L,
                errorMessage = "Error",
                stackTrace = null,
            )

        assertEquals(supportUrl, result)
    }

    @Test
    fun `build should return empty string when supportUrl uses a non-web scheme`() {
        for (supportUrl in listOf("file:///etc/passwd", "smb://attacker/share", "javascript:alert(1)", "not a url")) {
            val result =
                builder.build(
                    supportUrl = supportUrl,
                    scriptName = "Bot",
                    versionCode = 1L,
                    errorMessage = "Error",
                    stackTrace = null,
                )

            assertEquals("", result, "Expected non-web supportUrl to be rejected: $supportUrl")
        }
    }

    @Test
    fun `build should use default title when error message is blank`() {
        val result =
            builder.build(
                supportUrl = "https://github.com/owner/repo/issues",
                scriptName = "My Script",
                versionCode = 42L,
                errorMessage = "",
                stackTrace = null,
            )

        val rawTitle = result.substringAfter("title=").substringBefore("&")
        val titleDecoded = java.net.URLDecoder.decode(rawTitle, "UTF-8")
        assertEquals("[Script Error]", titleDecoded, "Title should be default when error message is blank")
    }

    @Test
    fun `build should handle GitHub issues URL with trailing slash`() {
        val result =
            builder.build(
                supportUrl = "https://github.com/owner/repo/issues/",
                scriptName = "Script",
                versionCode = 1L,
                errorMessage = "Error",
                stackTrace = null,
            )

        assertTrue(result.contains("/new"), "Expected /new in URL")
    }
}
