package com.cereal.client.application.script

import com.cereal.client.domain.model.url.WebUrl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MAX_STACK_TRACE_LENGTH = 3000

class GitHubIssueUrlBuilder {
    /**
     * Builds the URL to open for a script's support link. [supportUrl] comes from the script
     * manifest and is therefore untrusted, so non-http(s) values are rejected (returns an empty
     * string) rather than passed through to the OS URL handler. The downstream opener applies the
     * same allowlist, but rejecting here keeps a dangerous scheme from ever leaving this layer.
     */
    fun build(
        supportUrl: String,
        scriptName: String,
        versionCode: Long,
        errorMessage: String,
        stackTrace: String?,
    ): String {
        val webUrl = WebUrl.parse(supportUrl) ?: return ""

        val normalizedUrl = webUrl.value.trimEnd('/')

        if (!isGitHubIssuesUrl(normalizedUrl)) {
            return webUrl.value
        }

        val title = errorMessage.ifBlank { "[Script Error]" }
        val body = buildBody(scriptName, versionCode, errorMessage, stackTrace)

        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20")
        val encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20")

        return "$normalizedUrl/new?title=$encodedTitle&body=$encodedBody"
    }

    private fun isGitHubIssuesUrl(url: String): Boolean = url.contains("github.com") && (url.endsWith("/issues") || url.contains("/issues/"))

    private fun buildBody(
        scriptName: String,
        versionCode: Long,
        errorMessage: String,
        stackTrace: String?,
    ): String {
        val truncatedTrace =
            stackTrace
                ?.take(MAX_STACK_TRACE_LENGTH)
                ?.let { if (stackTrace.length > MAX_STACK_TRACE_LENGTH) "$it\n...[truncated]" else it }

        return buildString {
            appendLine("**Script:** $scriptName v$versionCode")
            appendLine("**Error:** $errorMessage")
            if (truncatedTrace != null) {
                appendLine()
                appendLine("**Stack trace:**")
                appendLine("```")
                appendLine(truncatedTrace)
                appendLine("```")
            }
        }.trim()
    }
}
