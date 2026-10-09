// PROTOTYPE — throwaway spike (#59). Raw OkHttp Anthropic Messages client, shaped like the #56 decision.
package spike

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class Claude(
    private val apiKey: String?, // null = OAuth token from the `ant` profile below
    private val antProfile: String,
    private val model: String,
    private val capture: File, // every request body, one per line, for the password grep + prefix diff
    private val clearTrigger: Int,
) {
    private val http = OkHttpClient.Builder().readTimeout(10, TimeUnit.MINUTES).build()

    // print-credentials refreshes the short-lived token when needed, so fetch it per request.
    private fun antToken(): String {
        val p = ProcessBuilder("ant", "--profile", antProfile, "auth", "print-credentials", "--access-token").start()
        val token = p.inputStream.bufferedReader().readText().trim()
        check(p.waitFor() == 0 && token.isNotEmpty()) { "ant token failed: ${p.errorStream.bufferedReader().readText()}" }
        return token
    }

    class Reply(val status: Int, val body: JsonObject, val requestId: String?)

    suspend fun call(system: String, tools: JsonArray, messages: List<JsonElement>): Reply {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16000)
            putJsonObject("thinking") {
                put("type", "adaptive")
                // Make the preserved-thinking history check explicit: a tripped check is a 400, not a silent drop.
                putJsonObject("block_binding") { put("prefix_mismatch_behavior", "error") }
            }
            putJsonObject("output_config") { put("effort", "medium") }
            putJsonObject("tool_choice") { put("type", "auto") }
            putJsonObject("cache_control") { put("type", "ephemeral") }
            put("fallbacks", "default")
            putJsonObject("context_management") {
                putJsonArray("edits") {
                    add(
                        buildJsonObject {
                            put("type", "clear_tool_uses_20250919")
                            putJsonObject("trigger") { put("type", "input_tokens"); put("value", clearTrigger) }
                            putJsonObject("keep") { put("type", "tool_uses"); put("value", 2) }
                        },
                    )
                }
            }
            put("system", system)
            put("tools", tools)
            put("messages", JsonArray(messages))
        }
        val text = body.toString()
        capture.appendText(text + "\n")
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .apply { if (apiKey != null) header("x-api-key", apiKey) else header("Authorization", "Bearer ${antToken()}") }
            .header("anthropic-version", "2023-06-01")
            .header(
                "anthropic-beta",
                (if (apiKey == null) "oauth-2025-04-20," else "") + "context-management-2025-06-27,server-side-fallback-2026-07-01,thinking-binding-controls-2026-08-01",
            )
            .post(text.toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { r ->
                Reply(r.code, Json.parseToJsonElement(r.body.string()).jsonObject, r.header("request-id"))
            }
        }
    }
}

// $/MTok for claude-opus-5-5: input 4, output 20, cache read 0.20, 5-min cache write 1.25x input.
fun costUsd(usage: JsonObject?): Double {
    fun n(k: String) = usage?.get(k)?.toString()?.toDoubleOrNull() ?: 0.0
    return (n("input_tokens") * 4.0 + n("cache_creation_input_tokens") * 5.0 +
        n("cache_read_input_tokens") * 0.20 + n("output_tokens") * 20.0) / 1_000_000
}
