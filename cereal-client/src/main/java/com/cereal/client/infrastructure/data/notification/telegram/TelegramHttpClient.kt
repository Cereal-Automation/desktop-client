package com.cereal.client.infrastructure.data.notification.telegram

import com.cereal.client.application.exception.CerealException
import com.cereal.client.infrastructure.data.notification.telegram.mapper.TelegramModelMapper
import com.cereal.client.infrastructure.data.notification.telegram.model.TelegramUpdate
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class TelegramHttpClient(
    private val baseUrl: String = "https://api.telegram.org",
) {
    private val logger = LoggerFactory.getLogger(TelegramHttpClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient: OkHttpClient = OkHttpClient().newBuilder().build()
    private val jsonMediaType = "application/json".toMediaTypeOrNull()

    suspend fun sendMessage(
        botToken: String,
        telegramMessage: TelegramMessage,
        maxAttempts: Int = 3,
    ) {
        val serializableMessage = TelegramModelMapper.toSerializable(telegramMessage)
        val requestBody =
            json
                .encodeToString(serializableMessage)
                .toRequestBody(jsonMediaType)

        val request =
            Request
                .Builder()
                .post(requestBody)
                .url("$baseUrl/bot$botToken/sendMessage")
                .build()

        logger.debug(
            "Attempting to send Telegram message: chatId={}, messageLength={}",
            telegramMessage.chatId,
            telegramMessage.text.length,
        )

        repeat(maxAttempts) { attempt ->
            when (val result = sendOnce(request)) {
                is SendResult.Success -> return
                is SendResult.PermanentFailure -> throw CerealException("Telegram rejected the message (HTTP ${result.statusCode}).")
                is SendResult.Retryable -> if (attempt < maxAttempts - 1) delay(result.delayMs)
            }
        }
        throw CerealException("Unable to deliver Telegram message after $maxAttempts attempts.")
    }

    /** Sends [text] with one row of inline buttons, (label, callback_data) each; returns the message id. */
    suspend fun sendMessageWithButtons(
        botToken: String,
        chatId: String,
        text: String,
        buttons: List<Pair<String, String>>,
    ): Long =
        call(
            botToken,
            "sendMessage",
            buildJsonObject {
                put("chat_id", chatId)
                put("text", text)
                putJsonObject("reply_markup") {
                    putJsonArray("inline_keyboard") {
                        addJsonArray {
                            buttons.forEach { (label, data) ->
                                addJsonObject {
                                    put("text", label)
                                    put("callback_data", data)
                                }
                            }
                        }
                    }
                }
            },
        ).jsonObject
            .getValue("message_id")
            .jsonPrimitive.long

    /** Long-polls for button presses; [offset] confirms every update before it. */
    suspend fun getUpdates(
        botToken: String,
        offset: Long?,
        timeoutSeconds: Int,
    ): List<TelegramUpdate> =
        json.decodeFromJsonElement<List<TelegramUpdate>>(
            call(
                botToken,
                "getUpdates",
                buildJsonObject {
                    offset?.let { put("offset", it) }
                    put("timeout", timeoutSeconds)
                    putJsonArray("allowed_updates") { add("callback_query") }
                },
                maxAttempts = 1,
                client = httpClient.newBuilder().readTimeout((timeoutSeconds + POLL_READ_MARGIN_SECONDS).toLong(), TimeUnit.SECONDS).build(),
            ),
        )

    suspend fun answerCallbackQuery(
        botToken: String,
        callbackQueryId: String,
        text: String?,
    ) {
        call(
            botToken,
            "answerCallbackQuery",
            buildJsonObject {
                put("callback_query_id", callbackQueryId)
                text?.let { put("text", it) }
            },
        )
    }

    /** Replaces the message's text; without a reply_markup Telegram drops its buttons. */
    suspend fun editMessageText(
        botToken: String,
        chatId: String,
        messageId: Long,
        text: String,
    ) {
        call(
            botToken,
            "editMessageText",
            buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", text)
            },
        )
    }

    /** One Bot API call with the same 429 back-off as [sendMessage]; returns its `result`. */
    private suspend fun call(
        botToken: String,
        method: String,
        body: JsonObject,
        maxAttempts: Int = 3,
        client: OkHttpClient = httpClient,
    ): JsonElement {
        val request =
            Request
                .Builder()
                .post(body.toString().toRequestBody(jsonMediaType))
                .url("$baseUrl/bot$botToken/$method")
                .build()
        repeat(maxAttempts) { attempt ->
            when (val result = sendOnce(request, client)) {
                is SendResult.Success -> return json.parseToJsonElement(result.body).jsonObject.getValue("result")
                is SendResult.PermanentFailure -> throw CerealException("Telegram rejected $method (HTTP ${result.statusCode}).")
                is SendResult.Retryable -> if (attempt < maxAttempts - 1) delay(result.delayMs)
            }
        }
        throw CerealException("Telegram $method failed after $maxAttempts attempts.")
    }

    private suspend fun sendOnce(
        request: Request,
        client: OkHttpClient = httpClient,
    ): SendResult =
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == HTTP_TOO_MANY_REQUESTS -> {
                            logger.warn("Rate limited by Telegram, retrying...")
                            SendResult.Retryable(retryAfterMillis(response.body.string()))
                        }

                        response.isSuccessful -> {
                            logger.debug("Successfully sent Telegram message")
                            SendResult.Success(response.body.string())
                        }

                        else -> {
                            logger.warn("Failed to send Telegram message: statusCode={}, body redacted", response.code)
                            SendResult.PermanentFailure(response.code)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            logger.warn("Unable to send Telegram message.", e)
            SendResult.Retryable(RETRY_DELAY_MS)
        }

    /** Telegram reports how long to back off in `parameters.retry_after` (seconds). */
    private fun retryAfterMillis(body: String): Long =
        runCatching {
            json
                .parseToJsonElement(body)
                .jsonObject["parameters"]
                ?.jsonObject
                ?.get("retry_after")
                ?.jsonPrimitive
                ?.longOrNull
        }.getOrNull()
            ?.let { it.seconds.inWholeMilliseconds.coerceIn(0L, MAX_RETRY_AFTER_MS) }
            ?: RETRY_DELAY_MS

    private sealed interface SendResult {
        data class Success(
            val body: String,
        ) : SendResult

        data class Retryable(
            val delayMs: Long,
        ) : SendResult

        data class PermanentFailure(
            val statusCode: Int,
        ) : SendResult
    }

    private companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_AFTER_MS = 30_000L
        private const val POLL_READ_MARGIN_SECONDS = 10
    }
}
