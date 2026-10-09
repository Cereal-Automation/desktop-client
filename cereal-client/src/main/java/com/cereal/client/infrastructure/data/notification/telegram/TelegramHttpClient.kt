package com.cereal.client.infrastructure.data.notification.telegram

import com.cereal.client.application.exception.CerealException
import com.cereal.client.infrastructure.data.notification.telegram.mapper.TelegramModelMapper
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
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
                SendResult.Success -> return
                is SendResult.PermanentFailure -> throw CerealException("Telegram rejected the message (HTTP ${result.statusCode}).")
                is SendResult.Retryable -> if (attempt < maxAttempts - 1) delay(result.delayMs)
            }
        }
        throw CerealException("Unable to deliver Telegram message after $maxAttempts attempts.")
    }

    private suspend fun sendOnce(request: Request): SendResult =
        try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == HTTP_TOO_MANY_REQUESTS -> {
                            logger.warn("Rate limited by Telegram, retrying...")
                            SendResult.Retryable(retryAfterMillis(response.body.string()))
                        }

                        response.isSuccessful -> {
                            logger.debug("Successfully sent Telegram message")
                            SendResult.Success
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
        data object Success : SendResult

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
    }
}
