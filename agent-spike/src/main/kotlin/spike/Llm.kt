// PROTOTYPE — throwaway spike (#59). Provider-neutral LLM seam with the two adapters decided in #56:
// native Anthropic Messages and OpenAI-compatible Chat Completions (here: Gemini). Raw OkHttp + kotlinx-serialization.
package spike

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class ToolCall(val id: String, val name: String, val argsJson: String)
class ToolDef(val name: String, val description: String, val schema: JsonObject)
class ToolOutput(val call: ToolCall, val text: String, val isError: Boolean)

class Turn(
    val status: Int,
    val error: String?,
    val calls: List<ToolCall>,
    val stop: String?,
    val inTok: Long, val outTok: Long, val cacheRead: Long, val cacheWrite: Long,
    val usd: Double,
    val model: String?,
    val notes: List<String>, // provider-specific signals worth logging (context edits, fallbacks, …)
    val text: String?,
)

interface Llm {
    val name: String
    suspend fun next(system: String, tools: List<ToolDef>, firstUser: String): Turn
    fun addResults(results: List<ToolOutput>)
}

private val http = OkHttpClient.Builder().readTimeout(10, TimeUnit.MINUTES).build()
private val JSON = "application/json".toMediaType()

private suspend fun post(url: String, headers: Map<String, String>, body: String, capture: File): Pair<Int, JsonObject> {
    capture.appendText(body + "\n")
    repeat(3) { // rate limits: wait what the server asks (Gemini RetryInfo "retryDelay": "2s"), then retry
        val r = send(url, headers, body)
        if (r.first != 429) return r
        val wait = Regex("retryDelay\\W+(\\d+)").find(r.second.toString())?.groupValues?.get(1)?.toLong() ?: 10
        System.err.println("429, retrying in ${wait + 1}s"); kotlinx.coroutines.delay((wait + 1) * 1000)
    }
    return send(url, headers, body)
}

private suspend fun send(url: String, headers: Map<String, String>, body: String): Pair<Int, JsonObject> {
    val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.post(body.toRequestBody(JSON)).build()
    return withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { r ->
            val text = r.body.string()
            r.code to (runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: buildJsonObject { put("raw", text) })
        }
    }
}

private fun JsonObject?.long(k: String) = this?.get(k)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

// ---------------------------------------------------------------- Anthropic Messages (native Claude path)
class AnthropicLlm(
    private val auth: () -> Pair<String, String>, // header name → value
    private val oauth: Boolean,
    private val model: String,
    private val capture: File,
    private val clearTrigger: Int,
) : Llm {
    override val name = "anthropic/$model"
    private val messages = mutableListOf<JsonElement>()

    override suspend fun next(system: String, tools: List<ToolDef>, firstUser: String): Turn {
        if (messages.isEmpty()) messages += buildJsonObject { put("role", "user"); put("content", firstUser) }
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
            putJsonArray("tools") {
                tools.forEach { t ->
                    add(buildJsonObject { put("name", t.name); put("description", t.description); put("input_schema", t.schema); put("strict", true) })
                }
            }
            put("messages", JsonArray(messages))
        }
        val (h, v) = auth()
        val betas = (if (oauth) "oauth-2025-04-20," else "") +
            "context-management-2025-06-27,server-side-fallback-2026-07-01,thinking-binding-controls-2026-08-01"
        val (status, r) = post(
            "https://api.anthropic.com/v1/messages",
            mapOf(h to v, "anthropic-version" to "2023-06-01", "anthropic-beta" to betas),
            body.toString(), capture,
        )
        if (status != 200) return Turn(status, r.toString(), emptyList(), null, 0, 0, 0, 0, 0.0, null, emptyList(), null)
        val usage = r["usage"]?.jsonObject
        val content = r["content"]!!.jsonArray
        // Append-only: the assistant content goes back exactly as received (thinking blocks included).
        messages += buildJsonObject { put("role", "assistant"); put("content", content) }
        val notes = buildList {
            r["context_management"]?.toString()?.takeIf { it != "null" && !it.contains("\"applied_edits\":[]") }?.let { add("context_management $it") }
            r["input_transformations"]?.toString()?.takeIf { it != "[]" }?.let { add("input_transformations $it") }
            r["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it != model }?.let { add("fallback fired → $it") }
            if (r["stop_reason"]?.jsonPrimitive?.contentOrNull == "refusal") add("refusal ${r["stop_details"]}")
        }
        val calls = content.map { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.content == "tool_use" }
            .map { ToolCall(it["id"]!!.jsonPrimitive.content, it["name"]!!.jsonPrimitive.content, it["input"].toString()) }
        // $/MTok for claude-opus-5-5: input 4, output 20, cache read 0.20, 5-min cache write 1.25x input.
        val usd = (usage.long("input_tokens") * 4.0 + usage.long("cache_creation_input_tokens") * 5.0 +
            usage.long("cache_read_input_tokens") * 0.20 + usage.long("output_tokens") * 20.0) / 1_000_000
        return Turn(
            200, null, calls, r["stop_reason"]?.jsonPrimitive?.contentOrNull,
            usage.long("input_tokens"), usage.long("output_tokens"), usage.long("cache_read_input_tokens"), usage.long("cache_creation_input_tokens"),
            usd, r["model"]?.jsonPrimitive?.contentOrNull, notes,
            content.lastOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
        )
    }

    override fun addResults(results: List<ToolOutput>) {
        messages += buildJsonObject {
            put("role", "user")
            put(
                "content",
                buildJsonArray {
                    results.forEach { o ->
                        add(buildJsonObject { put("type", "tool_result"); put("tool_use_id", o.call.id); put("content", o.text); if (o.isError) put("is_error", true) })
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- OpenAI-compatible Chat Completions (Gemini, Ollama, …)
class OpenAiCompatLlm(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val capture: File,
    private val usdPerMTok: Triple<Double, Double, Double>, // input, output (incl. thinking), cached input
    private val keepSnapshots: Int = 2,
) : Llm {
    override val name = "openai-compat/$model"
    private val messages = mutableListOf<JsonObject>()
    private var sendStrict = true

    override suspend fun next(system: String, tools: List<ToolDef>, firstUser: String): Turn {
        if (messages.isEmpty()) {
            messages += buildJsonObject { put("role", "system"); put("content", system) }
            messages += buildJsonObject { put("role", "user"); put("content", firstUser) }
        }
        trimOldSnapshots()
        fun body() = buildJsonObject {
            put("model", model)
            put("reasoning_effort", "medium")
            put("tool_choice", "auto")
            putJsonArray("tools") {
                tools.forEach { t ->
                    add(
                        buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name); put("description", t.description); put("parameters", t.schema)
                                if (sendStrict) put("strict", true)
                            }
                        },
                    )
                }
            }
            put("messages", JsonArray(messages))
        }
        val headers = mapOf("Authorization" to "Bearer $apiKey")
        var (status, r) = post("$baseUrl/chat/completions", headers, body().toString(), capture)
        val notes = mutableListOf<String>()
        if (status == 400 && sendStrict && r.toString().contains("strict")) { // provider doesn't accept `strict`: we validate ourselves anyway
            sendStrict = false
            notes += "provider rejected `strict`; resent without it"
            val retry = post("$baseUrl/chat/completions", headers, body().toString(), capture)
            status = retry.first; r = retry.second
        }
        if (status != 200) return Turn(status, r.toString(), emptyList(), null, 0, 0, 0, 0, 0.0, null, notes, null)
        val choice = r["choices"]!!.jsonArray.first().jsonObject
        val msg = choice["message"]!!.jsonObject
        // Append-only: the assistant message goes back exactly as received (Gemini 3 thought signatures ride in it).
        messages += msg
        val usage = r["usage"]?.jsonObject
        val cached = usage?.get("prompt_tokens_details")?.jsonObject.long("cached_tokens")
        val inTok = usage.long("prompt_tokens") - cached
        val outTok = usage.long("completion_tokens") // Gemini counts thinking tokens here (check vs total_tokens in the log)
        val total = usage.long("total_tokens")
        if (total != usage.long("prompt_tokens") + outTok) notes += "usage: total_tokens=$total ≠ prompt+completion; raw=$usage"
        val (pi, po, pc) = usdPerMTok
        val usd = (inTok * pi + cached * pc + (total - usage.long("prompt_tokens")).coerceAtLeast(outTok) * po) / 1_000_000
        val calls = msg["tool_calls"]?.jsonArray.orEmpty().map { it.jsonObject }.map {
            val f = it["function"]!!.jsonObject
            ToolCall(it["id"]!!.jsonPrimitive.content, f["name"]!!.jsonPrimitive.content, f["arguments"]!!.jsonPrimitive.content)
        }
        return Turn(
            200, null, calls, choice["finish_reason"]?.jsonPrimitive?.contentOrNull,
            inTok, outTok, cached, 0, usd, r["model"]?.jsonPrimitive?.contentOrNull, notes,
            (msg["content"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    override fun addResults(results: List<ToolOutput>) {
        results.forEach { o ->
            messages += buildJsonObject {
                put("role", "tool"); put("tool_call_id", o.call.id); put("content", if (o.isError) "ERROR: ${o.text}" else o.text)
            }
        }
    }

    // #56: on this path old snapshot results are trimmed on our side (no thinking blocks to protect).
    private fun trimOldSnapshots() {
        val toolIdx = messages.indices.filter { messages[it]["role"]?.jsonPrimitive?.content == "tool" }
        toolIdx.dropLast(keepSnapshots).forEach { i ->
            val m = messages[i]
            val c = m["content"]?.jsonPrimitive?.content.orEmpty()
            if (c.contains("<page_content>")) {
                messages[i] = JsonObject(m + ("content" to JsonPrimitive(c.substringBefore("<page_content>") + "[older snapshot removed]")))
            }
        }
    }
}
