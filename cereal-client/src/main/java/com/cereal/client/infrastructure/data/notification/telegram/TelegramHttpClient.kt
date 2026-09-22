package com.cereal.client.infrastructure.data.notification.telegram

import com.cereal.client.infrastructure.data.notification.telegram.mapper.TelegramModelMapper
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException

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

        repeat(maxAttempts) {
            when (sendOnce(request)) {
                SendResult.Success -> return
                SendResult.PermanentFailure -> return
                SendResult.Retryable -> delay(RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun sendOnce(request: Request): SendResult =
        try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    when {
                        responseBody.contains("Too Many Requests") -> {
                            logger.debug("Rate limited by Telegram, retrying...")
                            SendResult.Retryable
                        }

                        response.isSuccessful -> {
                            logger.debug("Successfully sent Telegram message")
                            SendResult.Success
                        }

                        else -> {
                            logger.warn("Failed to send Telegram message: statusCode={}, body redacted", response.code)
                            SendResult.PermanentFailure
                        }
                    }
                }
            }
        } catch (e: IOException) {
            logger.warn("Unable to send Telegram message.", e)
            SendResult.Retryable
        }

    private enum class SendResult { Success, Retryable, PermanentFailure }

    private companion object {
        private const val RETRY_DELAY_MS = 1000L
    }
}
