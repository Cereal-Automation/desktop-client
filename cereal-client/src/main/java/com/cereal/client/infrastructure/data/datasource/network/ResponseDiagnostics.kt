package com.cereal.client.infrastructure.data.datasource.network

import okhttp3.Response

/**
 * Non-sensitive response headers that identify the hop a response came from and whether it was
 * served from a cache — enough to tell an edge-generated error or a replayed cache entry apart from
 * a genuine backend failure when triaging a crash report.
 *
 * `cf-ray` is the most useful of them: it is the CDN's request id, so a report can be matched to the
 * exact request in the CDN log instead of guessing from client IP and timestamp.
 *
 * Deliberately an allow-list. Response headers can carry session data, so nothing outside this set
 * is ever attached to a crash report.
 */
fun Response.diagnosticHeaders(): Map<String, String> = DIAGNOSTIC_HEADERS.mapNotNull { name -> header(name)?.let { name to it } }.toMap()

private val DIAGNOSTIC_HEADERS =
    listOf("cf-ray", "cf-cache-status", "age", "x-cache", "via", "server", "content-type", "date")
